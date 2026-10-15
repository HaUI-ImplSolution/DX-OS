# SPDX-License-Identifier: Apache-2.0
"""The query context generated for the dashboard bundle charts and the bundle's own consistency."""

import json
import re
import unittest
import zipfile
from pathlib import Path

import yaml

import chart_query_context as qc

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "bi/farm_dashboard_export.zip"
FARM_CONFIG = ROOT / "backend/app-farm/src/main/java/HaUI/ImplSolution/dxos/farm/FarmPlatformConfig.java"
DATASOURCE_ID = 7


def read_bundle() -> dict[str, dict[str, dict]]:
    """Bundle files grouped by folder (charts, datasets, dashboards, databases), keyed by path."""
    groups: dict[str, dict[str, dict]] = {}
    with zipfile.ZipFile(BUNDLE) as bundle:
        for name in bundle.namelist():
            if not name.endswith(".yaml") or name.endswith("metadata.yaml"):
                continue
            folder = name.split("/")[1]
            groups.setdefault(folder, {})[name] = yaml.safe_load(bundle.read(name))
    return groups


def chart_by_name(name: str) -> dict:
    for chart in read_bundle()["charts"].values():
        if chart["slice_name"] == name:
            return chart
    raise AssertionError(f"chart {name!r} is not in the bundle")


def query_of(chart_name: str) -> dict:
    form_data = chart_by_name(chart_name)["params"]
    context = qc.build_query_context(form_data, DATASOURCE_ID)
    assert context is not None
    return context["queries"][0]


class BuildQueryContextTest(unittest.TestCase):
    def test_every_bundle_chart_gets_a_query_context(self):
        for chart in read_bundle()["charts"].values():
            with self.subTest(chart=chart["slice_name"]):
                context = qc.build_query_context(chart["params"], DATASOURCE_ID)
                self.assertIsNotNone(context)
                self.assertEqual({"id": DATASOURCE_ID, "type": "table"}, context["datasource"])
                self.assertEqual(1, len(context["queries"]))
                self.assertEqual(f"{DATASOURCE_ID}__table", context["form_data"]["datasource"])
                self.assertEqual(chart["viz_type"], context["form_data"]["viz_type"])
                self.assertEqual("full", context["result_type"])

    def test_bundle_chart_types_are_all_supported(self):
        for chart in read_bundle()["charts"].values():
            self.assertIn(chart["viz_type"], qc.SUPPORTED_VIZ_TYPES, chart["slice_name"])

    def test_pie_groups_by_column_and_sorts_by_metric(self):
        query = query_of("Vụ mùa theo trạng thái")
        metric = chart_by_name("Vụ mùa theo trạng thái")["params"]["metric"]
        self.assertEqual(["status"], query["columns"])
        self.assertEqual([metric], query["metrics"])
        self.assertEqual([[metric, False]], query["orderby"])
        self.assertEqual(100, query["row_limit"])

    def test_bar_chart_buckets_time_axis_and_pivots_series(self):
        query = query_of("Báo cáo bệnh theo tuần")
        self.assertEqual(
            {
                "timeGrain": "P1W",
                "columnType": "BASE_AXIS",
                "sqlExpression": "diagnosed_at",
                "label": "diagnosed_at",
                "expressionType": "SQL",
            },
            query["columns"][0],
        )
        self.assertEqual(["disease_name"], query["columns"][1:])
        pivot, flatten = query["post_processing"]
        self.assertEqual("pivot", pivot["operation"])
        self.assertEqual(["diagnosed_at"], pivot["options"]["index"])
        self.assertEqual(["disease_name"], pivot["options"]["columns"])
        self.assertEqual({"Số lượng": {"operator": "mean"}}, pivot["options"]["aggregates"])
        self.assertEqual({"operation": "flatten"}, flatten)

    def test_bar_chart_keeps_simple_filter(self):
        query = query_of("Lịch thu hoạch dự kiến")
        self.assertEqual([{"col": "status", "op": "==", "val": "ACTIVE"}], query["filters"])

    def test_raw_table_selects_columns_and_keeps_sql_filter(self):
        query = query_of("Thửa đang cách ly")
        self.assertEqual(
            ["parcel_code", "pesticide_name", "applied_at", "harvest_locked_until"], query["columns"]
        )
        self.assertEqual([], query["metrics"])
        self.assertEqual([["harvest_locked_until", True]], query["orderby"])
        self.assertEqual("(harvest_locked_until >= CURRENT_DATE)", query["extras"]["where"])
        self.assertEqual(100, query["row_limit"])

    def test_scatter_reads_spatial_columns_and_skips_null_points(self):
        query = query_of("Bản đồ thửa ruộng")
        self.assertEqual(["longitude", "latitude"], query["columns"])
        self.assertEqual([], query["metrics"])
        self.assertEqual(
            [
                {"col": "longitude", "op": "IS NOT NULL"},
                {"col": "latitude", "op": "IS NOT NULL"},
            ],
            query["filters"],
        )
        self.assertFalse(query["is_timeseries"])

    def test_heatmap_counts_points(self):
        query = query_of("Điểm nóng dịch bệnh")
        self.assertEqual(["longitude", "latitude"], query["columns"])
        self.assertEqual(["count"], query["metrics"])

    def test_metric_radius_becomes_scatter_metric(self):
        form_data = {
            "viz_type": qc.DECK_SCATTER,
            "spatial": {"type": "latlong", "lonCol": "lon", "latCol": "lat"},
            "point_radius_fixed": {"type": "metric", "value": "count"},
        }
        query = qc.build_query_context(form_data, DATASOURCE_ID)["queries"][0]
        self.assertEqual(["count"], query["metrics"])

    def test_having_clause_goes_to_having(self):
        form_data = {
            "viz_type": qc.PIE,
            "groupby": ["status"],
            "metric": "count",
            "adhoc_filters": [
                {"expressionType": "SQL", "clause": "HAVING", "sqlExpression": "COUNT(*) > 1"},
                {"expressionType": "SQL", "clause": "WHERE", "sqlExpression": "a = 1"},
                {"expressionType": "SQL", "clause": "WHERE", "sqlExpression": "b = 2"},
            ],
        }
        extras = qc.build_query_context(form_data, DATASOURCE_ID)["queries"][0]["extras"]
        self.assertEqual("(COUNT(*) > 1)", extras["having"])
        self.assertEqual("(a = 1) AND (b = 2)", extras["where"])

    def test_unsupported_visualization_has_no_query_context(self):
        self.assertIsNone(qc.build_query_context({"viz_type": "big_number_total"}, DATASOURCE_ID))

    def test_aggregate_table_has_no_query_context(self):
        form_data = {"viz_type": qc.TABLE, "query_mode": "aggregate", "groupby": ["status"]}
        self.assertIsNone(qc.build_query_context(form_data, DATASOURCE_ID))

    def test_payload_carries_serialized_context_and_generation_flag(self):
        context = qc.build_query_context(chart_by_name("Vụ mùa theo trạng thái")["params"], 3)
        payload = qc.to_payload(context)
        self.assertTrue(payload["query_context_generation"])
        self.assertEqual(context, json.loads(payload["query_context"]))


class DatasourceIdTest(unittest.TestCase):
    def test_reads_table_reference(self):
        self.assertEqual(12, qc.datasource_id_of({"datasource": "12__table"}))

    def test_rejects_other_references(self):
        for reference in (None, "", "12", "12__query", "abc__table", "__table"):
            with self.subTest(reference=reference):
                self.assertIsNone(qc.datasource_id_of({"datasource": reference}))


class BundleConsistencyTest(unittest.TestCase):
    def test_charts_point_to_datasets_of_the_bundle(self):
        groups = read_bundle()
        dataset_uuids = {d["uuid"] for d in groups["datasets"].values()}
        for chart in groups["charts"].values():
            self.assertIn(chart["dataset_uuid"], dataset_uuids, chart["slice_name"])

    def test_datasets_belong_to_the_bundle_database(self):
        groups = read_bundle()
        self.assertEqual(1, len(groups["databases"]))
        for path in groups["datasets"]:
            self.assertEqual("DX-Farm", path.split("/")[2])

    def test_database_password_is_masked(self):
        for database in read_bundle()["databases"].values():
            password = re.search(r"://[^:]+:([^@]*)@", database["sqlalchemy_uri"]).group(1)
            self.assertEqual("XXXXXXXXXX", password)

    def test_every_dataset_is_a_view_the_read_only_role_can_read(self):
        source = FARM_CONFIG.read_text(encoding="utf-8")
        granted = set(re.findall(r'"(vw_[a-z_]+)"', source))
        for dataset in read_bundle()["datasets"].values():
            self.assertIn(dataset["table_name"], granted)

    def test_dashboard_layout_places_every_chart_once_by_uuid(self):
        groups = read_bundle()
        chart_uuids = {c["uuid"] for c in groups["charts"].values()}
        for dashboard in groups["dashboards"].values():
            nodes = dashboard["position"]
            tiles = [n for n in nodes.values() if isinstance(n, dict) and n.get("type") == "CHART"]
            for tile in tiles:
                self.assertIn("uuid", tile["meta"], tile["meta"].get("sliceName"))
            placed = [tile["meta"]["uuid"] for tile in tiles]
            self.assertCountEqual(chart_uuids, placed)

    def test_dashboard_rows_fit_the_twelve_column_grid(self):
        for dashboard in read_bundle()["dashboards"].values():
            nodes = dashboard["position"]
            for node in nodes.values():
                if isinstance(node, dict) and node.get("type") == "ROW":
                    widths = [nodes[child]["meta"]["width"] for child in node["children"]]
                    self.assertLessEqual(sum(widths), 12, node["id"])

    def test_dashboard_layout_has_no_orphan_nodes(self):
        for dashboard in read_bundle()["dashboards"].values():
            nodes = dashboard["position"]
            reachable, queue = set(), ["ROOT_ID"]
            while queue:
                node_id = queue.pop()
                reachable.add(node_id)
                queue.extend(nodes[node_id].get("children", []))
            layout_nodes = {key for key, node in nodes.items() if isinstance(node, dict)}
            self.assertEqual(layout_nodes, reachable)


if __name__ == "__main__":
    unittest.main()
