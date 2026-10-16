# SPDX-License-Identifier: Apache-2.0
"""Builds the `query_context` of a Superset chart from its saved form data.

The dashboard import drops it, and `/api/v1/chart/{id}/data`, CSV export and reports need it.
"""

import json
from typing import Any

PIE = "pie"
TIMESERIES_BAR = "echarts_timeseries_bar"
TIMESERIES_LINE = "echarts_timeseries_line"
TABLE = "table"
DECK_SCATTER = "deck_scatter"
DECK_HEATMAP = "deck_heatmap"

SUPPORTED_VIZ_TYPES = (PIE, TIMESERIES_BAR, TIMESERIES_LINE, TABLE, DECK_SCATTER, DECK_HEATMAP)

DEFAULT_ROW_LIMIT = 10000


def datasource_id_of(form_data: dict[str, Any]) -> int | None:
    """Reads the dataset id from the `<id>__table` reference Superset keeps in the form data."""
    reference = str(form_data.get("datasource") or "")
    prefix, separator, kind = reference.partition("__")
    if separator and kind == "table" and prefix.isdigit():
        return int(prefix)
    return None


def build_query_context(form_data: dict[str, Any], datasource_id: int) -> dict[str, Any] | None:
    """Returns the query context of the chart, or None when its visualization is not supported."""
    viz_type = form_data.get("viz_type")
    builder = _QUERY_BUILDERS.get(viz_type)
    if builder is None or (viz_type == TABLE and form_data.get("query_mode") != "raw"):
        return None
    full_form_data = {
        **form_data,
        "datasource": f"{datasource_id}__table",
        "force": False,
        "result_format": "json",
        "result_type": "full",
    }
    return {
        "datasource": {"id": datasource_id, "type": "table"},
        "force": False,
        "queries": [builder(form_data)],
        "form_data": full_form_data,
        "result_format": "json",
        "result_type": "full",
    }


def to_payload(query_context: dict[str, Any]) -> dict[str, Any]:
    """Body of `PUT /api/v1/chart/{id}`; the flag keeps the Explore UI from regenerating it."""
    return {"query_context": json.dumps(query_context), "query_context_generation": True}


def _filters_and_extras(form_data: dict[str, Any]) -> tuple[list[dict[str, Any]], dict[str, str]]:
    filters: list[dict[str, Any]] = []
    where: list[str] = []
    having: list[str] = []
    for adhoc in form_data.get("adhoc_filters") or []:
        if adhoc.get("expressionType") == "SQL":
            expression = f"({adhoc['sqlExpression']})"
            (having if adhoc.get("clause") == "HAVING" else where).append(expression)
        else:
            filters.append(
                {"col": adhoc["subject"], "op": adhoc["operator"], "val": adhoc.get("comparator")}
            )
    extras = {"having": " AND ".join(having), "where": " AND ".join(where)}
    return filters, extras


def _base_query(form_data: dict[str, Any]) -> dict[str, Any]:
    filters, extras = _filters_and_extras(form_data)
    return {
        "filters": filters,
        "extras": extras,
        "applied_time_extras": {},
        "annotation_layers": form_data.get("annotation_layers") or [],
        "url_params": form_data.get("url_params") or {},
        "custom_params": {},
        "custom_form_data": {},
        "time_range": form_data.get("time_range") or "No filter",
        "row_limit": form_data.get("row_limit", DEFAULT_ROW_LIMIT),
        "series_limit": 0,
        "order_desc": True,
    }


def _metric_label(metric: Any) -> str:
    if isinstance(metric, dict):
        return metric.get("label") or metric.get("sqlExpression") or metric.get("optionName", "")
    return str(metric)


def _pie_query(form_data: dict[str, Any]) -> dict[str, Any]:
    metric = form_data["metric"]
    return {
        **_base_query(form_data),
        "columns": list(form_data.get("groupby") or []),
        "metrics": [metric],
        "orderby": [[metric, False]] if form_data.get("sort_by_metric") else [],
    }


def _timeseries_query(form_data: dict[str, Any]) -> dict[str, Any]:
    x_axis = form_data["x_axis"]
    groupby = list(form_data.get("groupby") or [])
    metrics = list(form_data.get("metrics") or [])
    time_grain = form_data.get("time_grain_sqla")
    x_column: Any = x_axis
    if time_grain:
        x_column = {
            "timeGrain": time_grain,
            "columnType": "BASE_AXIS",
            "sqlExpression": x_axis,
            "label": x_axis,
            "expressionType": "SQL",
        }
    return {
        **_base_query(form_data),
        "columns": [x_column, *groupby],
        "metrics": metrics,
        "orderby": [],
        "post_processing": [
            {
                "operation": "pivot",
                "options": {
                    "index": [x_axis],
                    "columns": groupby,
                    "aggregates": {
                        _metric_label(metric): {"operator": "mean"} for metric in metrics
                    },
                    "drop_missing_columns": False,
                },
            },
            {"operation": "flatten"},
        ],
        "time_offsets": [],
    }


def _raw_table_query(form_data: dict[str, Any]) -> dict[str, Any]:
    orderby = []
    for entry in form_data.get("order_by_cols") or []:
        column, ascending = json.loads(entry) if isinstance(entry, str) else entry
        orderby.append([column, bool(ascending)])
    return {
        **_base_query(form_data),
        "columns": list(form_data.get("all_columns") or []),
        "metrics": [],
        "orderby": orderby,
        "post_processing": [],
    }


def _spatial_columns(form_data: dict[str, Any]) -> list[str]:
    spatial = form_data.get("spatial") or {}
    return [column for column in (spatial.get("lonCol"), spatial.get("latCol")) if column]


def _deck_query(form_data: dict[str, Any], metrics: list[Any]) -> dict[str, Any]:
    spatial = _spatial_columns(form_data)
    query = _base_query(form_data)
    if form_data.get("filter_nulls"):
        query["filters"] = [
            *query["filters"],
            *({"col": column, "op": "IS NOT NULL"} for column in spatial),
        ]
    columns = list(spatial)
    for extra in (form_data.get("dimension"), *(form_data.get("js_columns") or [])):
        if extra and extra not in columns:
            columns.append(extra)
    return {
        **query,
        "columns": columns,
        "metrics": metrics,
        "orderby": [],
        "is_timeseries": False,
    }


def _deck_scatter_query(form_data: dict[str, Any]) -> dict[str, Any]:
    radius = form_data.get("point_radius_fixed") or {}
    metrics = [radius["value"]] if radius.get("type") == "metric" and radius.get("value") else []
    return _deck_query(form_data, metrics)


def _deck_heatmap_query(form_data: dict[str, Any]) -> dict[str, Any]:
    size = form_data.get("size")
    return _deck_query(form_data, [size] if size else [])


_QUERY_BUILDERS = {
    PIE: _pie_query,
    TIMESERIES_BAR: _timeseries_query,
    TIMESERIES_LINE: _timeseries_query,
    TABLE: _raw_table_query,
    DECK_SCATTER: _deck_scatter_query,
    DECK_HEATMAP: _deck_heatmap_query,
}
