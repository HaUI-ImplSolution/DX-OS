// SPDX-License-Identifier: Apache-2.0
(function () {
  'use strict';

  var REFRESH_MS = 30000;
  var SVG_NS = 'http://www.w3.org/2000/svg';
  var WINDOW_SECONDS = { '1h': 3600, '24h': 86400, '7d': 604800 };
  var timeFormat = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'medium' });
  var hourFormat = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' });
  var dayFormat = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit' });
  var PAGE_SIZE = 20;
  var PAGER_SPAN = 5;

  var EVENT_SERIES = [
    { key: 'blocked', label: 'Bị chặn', cls: 'blocked' },
    { key: 'limited', label: 'Quá nhiều lượt', cls: 'limited' },
    { key: 'error', label: 'Lỗi hệ thống', cls: 'error' },
    { key: 'slow', label: 'Phản hồi chậm', cls: 'slow' }
  ];

  // Names as on the portal; tiles follow the path of a request, then the supporting services.
  var SERVICES = [
    ['gateway', 'Gateway (APISIX)'],
    ['web', 'Ứng dụng chính'],
    ['backend', 'Backend API'],
    ['keycloak', 'Keycloak (đăng nhập)'],
    ['database', 'Cơ sở dữ liệu (PostgreSQL)'],
    ['storage', 'Lưu trữ ảnh'],
    ['ai', 'Dịch vụ AI'],
    ['bi', 'BI Dashboard'],
    ['chat', 'Kênh chat'],
    ['portal', 'Trang cổng vào']
  ];

  var ACTION_NAMES = {
    WORKSPACE_CREATED: 'Tạo đơn vị mới',
    WORKSPACE_LINKED: 'Gắn tài khoản chủ cho đơn vị',
    MEMBER_ADDED: 'Thêm thành viên vào đơn vị',
    PARCEL_DELETED: 'Xóa thửa ruộng',
    SEASON_DELETED: 'Xóa vụ mùa',
    REPORT_DELETED: 'Xóa báo cáo bệnh',
    HARVEST_OVERRIDE: 'Thu hoạch ngoài quy định',
    TICKET_CATEGORY_CREATED: 'Tạo nhóm phiếu sự cố',
    TICKET_CATEGORY_CHANGED: 'Sửa nhóm phiếu sự cố',
    SLA_POLICY_CHANGED: 'Đổi thời hạn xử lý phiếu',
    TICKET_ATTACHMENT_REMOVED: 'Xóa tệp đính kèm phiếu',
    ADMIN_ACTION_FAILED: 'Thao tác quản trị không thực hiện được'
  };

  // First match wins; the text is part of a request path.
  var FEATURES = [
    ['/diagnosis', 'Chẩn đoán bệnh bằng AI'],
    ['/knowledge', 'Hỏi đáp tri thức'],
    ['/disease-reports', 'Báo cáo bệnh'],
    ['/treatment', 'Ghi nhận phun thuốc'],
    ['/parcels', 'Thửa ruộng'],
    ['/crop-seasons', 'Vụ mùa'],
    ['/tickets', 'Phiếu sự cố'],
    ['/room-bookings', 'Đặt phòng máy'],
    ['/device-loans', 'Mượn thiết bị'],
    ['/workspaces', 'Đơn vị và tài khoản'],
    ['/notifications', 'Thông báo'],
    ['/system/status', 'Trạng thái hệ thống'],
    ['/audit-logs', 'Nhật ký truy cập']
  ];

  var windowSelect = document.getElementById('window');
  var loading = false;
  var last = {};
  var zoom = null;
  // Columns grow in after a zoom, a reset or a change of window, but not at every refresh.
  var animateNext = true;

  function node(tag, text, cls) {
    var element = document.createElement(tag);
    if (text !== undefined && text !== null) {
      element.textContent = text;
    }
    if (cls) {
      element.className = cls;
    }
    return element;
  }

  function clear(element) {
    while (element.firstChild) {
      element.removeChild(element.firstChild);
    }
  }

  function svg(tag, attributes) {
    var element = document.createElementNS(SVG_NS, tag);
    Object.keys(attributes || {}).forEach(function (name) {
      element.setAttribute(name, attributes[name]);
    });
    return element;
  }

  function svgText(text, attributes) {
    var element = svg('text', attributes);
    element.textContent = text;
    return element;
  }

  function formatTime(iso) {
    return iso ? timeFormat.format(new Date(iso)) : '';
  }

  // "14:30", with the day in front when it is not today: "25/10 14:30".
  function clock(value) {
    var date = new Date(value);
    var today = new Date();
    var sameDay = date.getFullYear() === today.getFullYear() && date.getMonth() === today.getMonth()
      && date.getDate() === today.getDate();
    var day = ('0' + date.getDate()).slice(-2) + '/' + ('0' + (date.getMonth() + 1)).slice(-2);
    return (sameDay ? '' : day + ' ') + hourFormat.format(date);
  }

  function durationText(milliseconds) {
    var minutes = Math.round(milliseconds / 60000);
    if (minutes < 1) {
      return 'dưới 1 phút';
    }
    if (minutes < 60) {
      return minutes + ' phút';
    }
    var hours = Math.floor(minutes / 60);
    return hours + ' tiếng' + (minutes % 60 ? ' ' + (minutes % 60) + ' phút' : '');
  }

  // "14:00 - 15:00 (1 tiếng)"
  function spanText(from, to) {
    return clock(from) + ' - ' + clock(to) + ' (' + durationText(to - from) + ')';
  }

  function ago(iso) {
    var seconds = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
    if (seconds < 60) {
      return 'vừa xong';
    }
    if (seconds < 3600) {
      return Math.floor(seconds / 60) + ' phút trước';
    }
    if (seconds < 86400) {
      return Math.floor(seconds / 3600) + ' giờ trước';
    }
    return formatTime(iso);
  }

  function axisLabel(iso, spanSeconds) {
    var date = new Date(iso);
    return spanSeconds > 86400 * 2 ? dayFormat.format(date) : hourFormat.format(date);
  }

  function serviceIndex(key) {
    for (var i = 0; i < SERVICES.length; i++) {
      if (SERVICES[i][0] === key) {
        return i;
      }
    }
    return SERVICES.length;
  }

  function serviceName(key) {
    var index = serviceIndex(key);
    return index < SERVICES.length ? SERVICES[index][1] : key;
  }

  function featureName(path) {
    for (var i = 0; i < FEATURES.length; i++) {
      if (path.indexOf(FEATURES[i][0]) >= 0) {
        return FEATURES[i][1];
      }
    }
    return 'Chức năng khác';
  }

  function fetchData(path) {
    return fetch(path, {
      credentials: 'same-origin',
      redirect: 'manual',
      headers: { Accept: 'application/json' }
    }).then(function (response) {
      if (response.type === 'opaqueredirect' || response.status === 401 || response.status === 403) {
        window.location.reload();
        throw new Error('signed out');
      }
      return response.json().then(function (body) {
        if (!response.ok) {
          throw new Error(body && body.message ? body.message : 'Lỗi ' + response.status);
        }
        return body.data;
      });
    });
  }

  function width(holder) {
    return Math.max(holder.clientWidth || 0, 280);
  }

  // The smallest 1, 2 or 5 times a power of ten that is not below the value.
  function niceMax(value) {
    if (value <= 1) {
      return 1;
    }
    var power = Math.pow(10, Math.floor(Math.log(value) / Math.LN10));
    var steps = [1, 2, 5, 10];
    for (var i = 0; i < steps.length; i++) {
      if (steps[i] * power >= value) {
        return steps[i] * power;
      }
    }
    return 10 * power;
  }

  function emptyChart(holder, height, text) {
    clear(holder);
    // The view box is as wide as the holder, so the message keeps its size and the chart its height.
    var w = width(holder);
    var chart = svg('svg', { viewBox: '0 0 ' + w + ' ' + height, role: 'img' });
    chart.appendChild(svgText(text, { x: w / 2, y: height / 2, 'text-anchor': 'middle', class: 'empty' }));
    holder.appendChild(chart);
  }

  // When the first and the last event of a column happened: "14:10 - 14:50 (40 phút)".
  function eventSpan(first, last) {
    var from = new Date(first).getTime();
    var to = new Date(last).getTime();
    return hourFormat.format(new Date(from)) === hourFormat.format(new Date(to))
      ? clock(from) : spanText(from, to);
  }

  // Stacked columns, one per column of time. Values are drawn from attributes and classes only, so
  // the page needs no inline style. Dragging across the chart zooms into the chosen span.
  function drawColumns(holder, timeline, buckets, series, height, emptyText) {
    clear(holder);
    var totals = buckets.map(function (bucket) {
      return series.reduce(function (sum, item) { return sum + (bucket[item.key] || 0); }, 0);
    });
    var peak = Math.max.apply(null, totals.concat([0]));
    if (!buckets.length || peak === 0) {
      emptyChart(holder, height, emptyText);
      return;
    }
    var w = width(holder);
    var left = 40;
    var bottom = 26;
    var top = 10;
    var plotWidth = w - left - 8;
    var plotHeight = height - bottom - top;
    var max = niceMax(peak);
    var chart = svg('svg', { viewBox: '0 0 ' + w + ' ' + height, role: 'img', class: 'zoomable' + (animateNext ? ' grow' : '') });
    holder.appendChild(chart);
    [0, 0.5, 1].forEach(function (fraction) {
      var y = top + plotHeight - fraction * plotHeight;
      chart.appendChild(svg('line', { x1: left, y1: y, x2: w - 8, y2: y, class: fraction === 0 ? 'axis' : 'gridline' }));
      chart.appendChild(svgText(String(Math.round(max * fraction * 10) / 10), { x: left - 6, y: y + 4, 'text-anchor': 'end' }));
    });
    var slot = plotWidth / buckets.length;
    var barWidth = Math.min(48, Math.max(2, slot * 0.7));
    // Time marks sit on the start edge of a column, the same axis a dragged selection is read from.
    var spanSeconds = timeline.buckets.length * timeline.bucketSeconds;
    // Every few columns, evenly, so the marks are the same distance apart.
    var step = Math.max(1, Math.ceil(buckets.length / Math.max(2, Math.floor(plotWidth / 90))));
    var markIndexes = [];
    for (var m = 0; m < buckets.length; m += step) {
      markIndexes.push(m);
    }
    markIndexes.forEach(function (index) {
      var x = left + index * slot;
      chart.appendChild(svg('line', { x1: x, y1: top, x2: x, y2: top + plotHeight, class: 'vgrid' }));
    });
    buckets.forEach(function (bucket, index) {
      var x = left + index * slot + (slot - barWidth) / 2;
      var y = top + plotHeight;
      var group = svg('g');
      var lines = [];
      if (bucket.first) {
        lines.push(eventSpan(bucket.first, bucket.last));
      }
      series.forEach(function (item) {
        var value = bucket[item.key] || 0;
        if (value > 0) {
          var barHeight = (value / max) * plotHeight;
          y -= barHeight;
          var bar = svg('rect', { x: x, y: y, width: barWidth, height: barHeight, class: 's-' + item.cls });
          if (animateNext) {
            bar.style.animationDelay = Math.round(index / buckets.length * 300) + 'ms';
          }
          group.appendChild(bar);
          lines.push(item.label + ': ' + value);
        }
      });
      var tip = svg('title');
      tip.textContent = lines.join('\n');
      group.appendChild(tip);
      chart.appendChild(group);
    });
    markIndexes.forEach(function (index) {
      var x = Math.min(left + index * slot, w - 22);
      chart.appendChild(svgText(axisLabel(buckets[index].start, spanSeconds), { x: x, y: height - 6, 'text-anchor': 'middle' }));
    });
    enableZoom(chart, timeline, { w: w, left: left, top: top, plotWidth: plotWidth, plotHeight: plotHeight });
  }

  // Drag over the plot to choose a span; the charts then load it in finer columns.
  function enableZoom(chart, timeline, plot) {
    var selection = svg('rect', { class: 'selection', x: 0, y: plot.top, width: 0, height: plot.plotHeight });
    chart.appendChild(selection);
    var origin = null;
    var firstColumn = new Date(timeline.buckets[0].start).getTime();

    function position(event) {
      var box = chart.getBoundingClientRect();
      var x = (event.clientX - box.left) * (plot.w / box.width);
      return Math.max(plot.left, Math.min(x, plot.left + plot.plotWidth));
    }

    chart.addEventListener('pointerdown', function (event) {
      var x = position(event);
      if (event.button !== 0 || x < plot.left) {
        return;
      }
      origin = x;
      chart.setPointerCapture(event.pointerId);
      selection.setAttribute('x', x);
      selection.setAttribute('width', 0);
    });
    chart.addEventListener('pointermove', function (event) {
      if (origin === null) {
        return;
      }
      var x = position(event);
      selection.setAttribute('x', Math.min(origin, x));
      selection.setAttribute('width', Math.abs(x - origin));
    });
    chart.addEventListener('pointerup', function (event) {
      if (origin === null) {
        return;
      }
      var x = position(event);
      var begin = Math.min(origin, x);
      var end = Math.max(origin, x);
      origin = null;
      // The span snaps to the edges of the columns, so a drag that starts on a time mark zooms from
      // exactly that time.
      var columns = timeline.buckets.length;
      var first = Math.round((begin - plot.left) / plot.plotWidth * columns);
      var lastEdge = Math.round((end - plot.left) / plot.plotWidth * columns);
      if (lastEdge === first) {
        lastEdge = Math.min(columns, first + 1);
        first = Math.max(0, lastEdge - 1);
      }
      var columnMs = timeline.bucketSeconds * 1000;
      var from = Math.max(firstColumn + first * columnMs, new Date(timeline.from).getTime());
      var to = Math.min(firstColumn + lastEdge * columnMs, new Date(timeline.to).getTime());
      selection.setAttribute('width', 0);
      if (end - begin > 8 && to - from >= 60000) {
        setZoom(from, to);
      }
    });
    chart.addEventListener('pointercancel', function () {
      origin = null;
      selection.setAttribute('width', 0);
    });
  }

  function drawDonut(holder, parts, centerLabel) {
    clear(holder);
    var total = parts.reduce(function (sum, part) { return sum + part.value; }, 0);
    var chart = svg('svg', { viewBox: '0 0 200 200', role: 'img' });
    var radius = 70;
    var circumference = 2 * Math.PI * radius;
    chart.appendChild(svg('circle', { cx: 100, cy: 100, r: radius, fill: 'none', 'stroke-width': 26, class: 'd-track' }));
    var offset = 0;
    if (total > 0) {
      parts.forEach(function (part) {
        if (part.value <= 0) {
          return;
        }
        var length = (part.value / total) * circumference;
        var arc = svg('circle', {
          cx: 100, cy: 100, r: radius, fill: 'none', 'stroke-width': 26, class: 'd-' + part.cls,
          'stroke-dasharray': length + ' ' + (circumference - length),
          'stroke-dashoffset': -offset,
          transform: 'rotate(-90 100 100)'
        });
        var tip = svg('title');
        tip.textContent = part.label + ': ' + part.value;
        arc.appendChild(tip);
        chart.appendChild(arc);
        offset += length;
      });
    }
    chart.appendChild(svgText(String(total), { x: 100, y: 104, 'text-anchor': 'middle', class: 'center-value' }));
    chart.appendChild(svgText(centerLabel, { x: 100, y: 124, 'text-anchor': 'middle', class: 'center-label' }));
    holder.appendChild(chart);
    var list = node('ul');
    parts.forEach(function (part) {
      var item = node('li', null, 'l-' + part.cls);
      item.appendChild(node('span', part.label + ': ' + part.value));
      list.appendChild(item);
    });
    holder.appendChild(list);
  }

  // Horizontal bars with the label on the left; onClick (optional) receives the item.
  function drawBars(holder, items, cls, emptyText, onClick) {
    clear(holder);
    if (!items.length) {
      emptyChart(holder, 180, emptyText);
      return;
    }
    var w = width(holder);
    var rowHeight = 38;
    var height = items.length * rowHeight + 6;
    var labelWidth = Math.min(210, Math.round(w * 0.42));
    var barStart = labelWidth + 8;
    var barLength = Math.max(40, w - barStart - 46);
    var max = Math.max.apply(null, items.map(function (item) { return item.value; }));
    var chart = svg('svg', { viewBox: '0 0 ' + w + ' ' + height, role: 'img' });
    items.forEach(function (item, index) {
      var y = index * rowHeight + 3;
      var group = svg('g', onClick ? { class: 'clickable', tabindex: 0, role: 'button' } : {});
      var label = item.label.length > 30 ? item.label.slice(0, 29) + '…' : item.label;
      group.appendChild(svgText(label, { x: 0, y: y + 20, class: 'label' }));
      group.appendChild(svg('rect', { x: barStart, y: y + 6, width: barLength, height: 18, rx: 4, class: 's-track' }));
      group.appendChild(svg('rect', {
        x: barStart, y: y + 6, width: Math.max(4, (item.value / max) * barLength), height: 18, rx: 4, class: 's-' + cls
      }));
      group.appendChild(svgText(String(item.value), { x: w, y: y + 20, 'text-anchor': 'end', class: 'value' }));
      var tip = svg('title');
      tip.textContent = item.label + ': ' + item.value;
      group.appendChild(tip);
      if (onClick) {
        group.addEventListener('click', function () { onClick(item); });
        group.addEventListener('keydown', function (event) {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onClick(item);
          }
        });
      }
      chart.appendChild(group);
    });
    holder.appendChild(chart);
  }

  // One row per service: green while it ran, red while it was down, grey where nothing is known.
  function availability(service, changes, start, end) {
    var events = changes.filter(function (change) { return change.service === service; })
      .sort(function (a, b) { return new Date(a.at) - new Date(b.at); });
    var state = null;
    var cursor = start;
    var segments = [];
    events.forEach(function (event) {
      var at = new Date(event.at).getTime();
      if (at <= start) {
        state = event.state;
        return;
      }
      segments.push({ from: cursor, to: at, state: state });
      cursor = at;
      state = event.state;
    });
    segments.push({ from: cursor, to: end, state: state });
    var up = 0;
    var down = 0;
    segments.forEach(function (segment) {
      if (segment.state === 'UP') { up += segment.to - segment.from; }
      if (segment.state === 'DOWN') { down += segment.to - segment.from; }
    });
    return { segments: segments, percent: up + down > 0 ? up / (up + down) * 100 : null };
  }

  // One tile per service: the state now, the share of time it ran, and a strip of slots that are
  // red when the service was down at any moment of the slot.
  function drawServices(holder, health, windowCode) {
    clear(holder);
    if (!health.services.length) {
      holder.appendChild(node('p', 'Chưa có dữ liệu dò dịch vụ.', 'muted'));
      return;
    }
    // The strip starts at the first record when the system has been watched for less than the window.
    var end = Date.now();
    var start = end - WINDOW_SECONDS[windowCode] * 1000;
    var first = Infinity;
    health.changes.forEach(function (change) {
      first = Math.min(first, new Date(change.at).getTime());
    });
    if (first !== Infinity) {
      start = Math.max(start, first - 60000);
    }
    health.services.slice().sort(function (a, b) {
      return serviceIndex(a.service) - serviceIndex(b.service);
    }).forEach(function (service) {
      var data = availability(service.service, health.changes, start, end);
      var down = service.state === 'DOWN';
      var tile = node('div', null, 'tile ' + (down ? 'bad' : service.state === 'UP' ? 'ok' : 'unknown'));
      tile.appendChild(node('div', serviceName(service.service), 'name'));
      tile.appendChild(node('div', down ? 'Đã ngừng' : service.state === 'UP' ? 'Đang chạy' : 'Chưa rõ', 'state'));
      var percent = data.percent === null ? 'Chưa đủ dữ liệu' : 'Chạy được ' + (Math.round(data.percent * 10) / 10) + '% thời gian';
      tile.appendChild(node('div', percent, 'note'));
      var merged = mergeSegments(data.segments);
      tile.appendChild(timeBar(merged, start, end));
      var labels = node('div', null, 'blocks-label');
      labels.appendChild(node('span', axisLabel(new Date(start).toISOString(), WINDOW_SECONDS[windowCode])));
      labels.appendChild(node('span', 'Bây giờ'));
      tile.appendChild(labels);
      tile.appendChild(incidents(merged, end));
      holder.appendChild(tile);
    });
    var legend = node('ul', null, 'legend');
    [['up', 'Tốt'], ['down', 'Ngừng'], ['unknown', 'Trống dữ liệu']].forEach(function (item) {
      var entry = node('li', null, 'l-' + item[0]);
      entry.appendChild(node('span', item[1]));
      legend.appendChild(entry);
    });
    holder.appendChild(legend);
  }

  // Neighbouring stretches of the same state become one.
  function mergeSegments(segments) {
    var merged = [];
    segments.forEach(function (segment) {
      var previous = merged[merged.length - 1];
      if (previous && previous.state === segment.state) {
        previous.to = segment.to;
      } else {
        merged.push({ from: segment.from, to: segment.to, state: segment.state });
      }
    });
    return merged;
  }

  // What the bar cannot show well: each outage and the gap without data, as plain lines.
  function incidents(merged, end) {
    var list = node('ul', null, 'incidents');
    var downs = merged.filter(function (segment) { return segment.state === 'DOWN'; })
      .sort(function (a, b) { return b.from - a.from; });
    var shown = downs.slice(0, 3);
    shown.forEach(function (segment) {
      var ongoing = segment.to >= end - 1000;
      list.appendChild(node('li', 'Ngừng ' + (ongoing
        ? clock(segment.from) + ' - nay (' + durationText(segment.to - segment.from) + ', chưa phục hồi)'
        : spanText(segment.from, segment.to)), 'down'));
    });
    if (downs.length > shown.length) {
      list.appendChild(node('li', 'và ' + (downs.length - shown.length) + ' lần ngừng khác', 'more'));
    }
    var gap = merged.filter(function (segment) { return segment.state === null; })[0];
    // A gap of a few minutes is only the monitor starting up; the bar still shows it.
    if (gap && gap.to - gap.from >= 5 * 60000) {
      list.appendChild(node('li', 'Trống dữ liệu ' + spanText(gap.from, gap.to), 'unknown'));
    }
    if (!list.firstChild) {
      list.appendChild(node('li', 'Hoạt động tốt', 'ok'));
    }
    return list;
  }

  // One bar per service. A stretch of the same state is one block, so the hover time is exact.
  // Stretches too short to see (an outage of a minute in a day) are drawn at a minimum width.
  function timeBar(merged, start, end) {
    var total = 1000;
    var span = end - start;
    var bar = svg('svg', { viewBox: '0 0 ' + total + ' 16', preserveAspectRatio: 'none', role: 'img' });
    var ordered = merged.filter(function (segment) { return segment.state === 'UP'; })
      .concat(merged.filter(function (segment) { return segment.state !== 'UP'; }));
    ordered.forEach(function (segment) {
      var cls = segment.state === 'UP' ? 'up' : segment.state === 'DOWN' ? 'down' : 'unknown';
      var x = (segment.from - start) / span * total;
      var width = (segment.to - segment.from) / span * total;
      if (cls !== 'up' && width < 12) {
        x = Math.max(0, Math.min(x - (12 - width) / 2, total - 12));
        width = 12;
      }
      var rect = svg('rect', { x: x, y: 0, width: width, height: 16, class: 's-' + cls });
      var tip = svg('title');
      var ongoing = segment.to >= end - 1000;
      tip.textContent = (cls === 'up' ? 'Tốt' : cls === 'down' ? 'Ngừng' : 'Trống dữ liệu') + '\n'
        + (ongoing && cls === 'down'
          ? clock(segment.from) + ' - nay (' + durationText(segment.to - segment.from) + ')'
          : spanText(segment.from, segment.to));
      rect.appendChild(tip);
      bar.appendChild(rect);
    });
    return bar;
  }

  function renderBanner(summary, health) {
    var banner = document.getElementById('banner');
    var title = document.getElementById('banner-title');
    var text = document.getElementById('banner-text');
    var down = health.services.filter(function (service) { return service.state === 'DOWN'; });
    var level = 'ok';
    if (down.length) {
      level = 'bad';
      title.textContent = down.length === 1 ? 'Có 1 dịch vụ đang ngừng hoạt động' : 'Có ' + down.length + ' dịch vụ đang ngừng hoạt động';
      text.textContent = 'Ngừng: ' + down.map(function (service) { return serviceName(service.service); }).join(', ') + '.';
    } else if (summary.serverErrors > 0 || summary.denied >= 10 || summary.rateLimited >= 10
      || (summary.failedLogins !== null && summary.failedLogins >= 5)) {
      level = 'warn';
      title.textContent = 'Cần chú ý: có dấu hiệu bất thường';
      text.textContent = 'Mọi dịch vụ đang chạy, nhưng có lượt truy cập bị chặn, lỗi hệ thống hoặc đăng nhập sai nhiều hơn bình thường.';
    } else {
      title.textContent = 'Hệ thống hoạt động bình thường';
      text.textContent = 'Mọi dịch vụ đang chạy và không có dấu hiệu bất thường.';
    }
    banner.className = 'banner ' + level;
  }

  function card(label, value, cls, note) {
    var element = node('div', null, 'kpi' + (cls ? ' ' + cls : ''));
    element.appendChild(node('div', label, 'label'));
    element.appendChild(node('div', value, 'value'));
    element.appendChild(node('div', note, 'note'));
    return element;
  }

  function level(count, warnAt, badAt) {
    if (count >= badAt) {
      return 'bad';
    }
    return count >= warnAt ? 'warn' : 'ok';
  }

  function renderSummary(summary, health) {
    var holder = document.getElementById('summary');
    clear(holder);
    var total = health.services.length;
    var running = total - summary.servicesDown;
    holder.appendChild(card('Dịch vụ đang chạy', total ? running + '/' + total : '—', summary.servicesDown ? 'bad' : 'ok', 'Các phần của hệ thống đang hoạt động'));
    holder.appendChild(card('Truy cập bị chặn', summary.denied, level(summary.denied, 10, 50), 'Lượt truy cập không đủ quyền'));
    holder.appendChild(card('Lỗi hệ thống', summary.serverErrors, level(summary.serverErrors, 1, 5), 'Lần hệ thống không xử lý được yêu cầu'));
    holder.appendChild(card('Truy cập quá nhiều', summary.rateLimited, level(summary.rateLimited, 10, 100), 'Lượt bị tạm giới hạn vì gửi dồn dập'));
    if (summary.failedLogins === null) {
      holder.appendChild(card('Đăng nhập sai', '—', '', 'Chưa đọc được dữ liệu đăng nhập'));
    } else {
      holder.appendChild(card('Đăng nhập sai', summary.failedLogins, level(summary.failedLogins, 5, 20), 'Lần nhập sai tài khoản hoặc mật khẩu'));
    }
  }

  function categories(bucket) {
    return {
      start: bucket.start,
      blocked: bucket.denied + bucket.authRejected,
      limited: bucket.rateLimited,
      error: bucket.serverErrors,
      slow: bucket.slow,
      first: bucket.firstAt,
      last: bucket.lastAt,
      failedLogins: bucket.failedLogins,
      loginFirst: bucket.loginFirstAt,
      loginLast: bucket.loginLastAt
    };
  }

  function renderZoomNotes(timeline) {
    var zoomed = timeline.window === 'custom';
    ['zoom-events', 'zoom-logins'].forEach(function (id) {
      var bar = document.getElementById(id);
      bar.hidden = !zoomed;
      if (zoomed) {
        bar.firstChild.textContent = 'Đang phóng to: ' + spanText(new Date(timeline.from).getTime(), new Date(timeline.to).getTime()) + ' ';
      }
    });
  }

  function renderTimeline(timeline) {
    var buckets = timeline.buckets.map(categories);
    renderZoomNotes(timeline);
    drawColumns(document.getElementById('chart-events'), timeline, buckets, EVENT_SERIES, 280, 'Không có sự việc bất thường');
    var legend = document.getElementById('legend-events');
    clear(legend);
    var totals = {};
    EVENT_SERIES.forEach(function (item) {
      totals[item.key] = buckets.reduce(function (sum, bucket) { return sum + (bucket[item.key] || 0); }, 0);
      var entry = node('li', null, 'l-' + item.cls);
      entry.appendChild(node('span', item.label + ': ' + totals[item.key]));
      legend.appendChild(entry);
    });
    drawDonut(document.getElementById('chart-share'), EVENT_SERIES.map(function (item) {
      return { label: item.label, value: totals[item.key], cls: item.cls };
    }), 'sự việc');
    var holder = document.getElementById('chart-logins');
    if (buckets.length && buckets[0].failedLogins === null) {
      emptyChart(holder, 220, 'Chưa đọc được dữ liệu đăng nhập');
    } else {
      drawColumns(holder, timeline, buckets.map(function (bucket) {
        return { start: bucket.start, failedLogins: bucket.failedLogins || 0, first: bucket.loginFirst, last: bucket.loginLast };
      }), [{ key: 'failedLogins', label: 'Đăng nhập sai', cls: 'login' }], 220, 'Không có lần đăng nhập sai');
    }
  }

  function renderSuspects(data) {
    var sources = data.addresses.map(function (item) {
      return { label: 'Nguồn ' + item.ip, value: item.denied + item.rateLimited + item.authRejected + item.failedLogins, ip: item.ip };
    }).filter(function (item) { return item.value > 0; }).slice(0, 5);
    drawBars(document.getElementById('chart-sources'), sources, 'blocked', 'Không có nguồn đáng ngờ', function (item) {
      document.getElementById('trace-request').value = '';
      document.getElementById('trace-ip').value = item.ip;
      document.getElementById('tech').open = true;
      runTrace();
      document.getElementById('tech').scrollIntoView({ behavior: 'smooth' });
    });
    var byFeature = {};
    data.errorPaths.forEach(function (item) {
      var name = featureName(item.path);
      byFeature[name] = (byFeature[name] || 0) + item.count;
    });
    var features = Object.keys(byFeature).map(function (name) { return { label: name, value: byFeature[name] }; })
      .sort(function (a, b) { return b.value - a.value; }).slice(0, 5);
    drawBars(document.getElementById('chart-features'), features, 'error', 'Không có lỗi hệ thống');
  }

  function renderActivity(actions) {
    var list = document.getElementById('activity');
    clear(list);
    if (!actions.length) {
      list.appendChild(node('li', 'Chưa có thao tác nào.', 'empty'));
      return;
    }
    actions.slice(0, 8).forEach(function (item) {
      var row = node('li');
      var left = node('div');
      left.appendChild(node('div', ACTION_NAMES[item.action] || item.action, 'what'));
      left.appendChild(node('div', 'bởi ' + (item.actor || 'hệ thống'), 'who'));
      row.appendChild(left);
      var right = node('div');
      if (item.outcome === 'FAILED') {
        right.appendChild(node('div', 'không thành công', 'fail'));
      }
      right.appendChild(node('div', ago(item.at), 'when'));
      row.appendChild(right);
      list.appendChild(row);
    });
  }

  var traceEntries = [];
  var tracePage = 0;

  // Shows one page of the records fetched; the API returns at most 200 of them.
  function fillTrace() {
    var body = document.querySelector('#trace tbody');
    clear(body);
    var pages = Math.max(1, Math.ceil(traceEntries.length / PAGE_SIZE));
    tracePage = Math.min(tracePage, pages - 1);
    if (!traceEntries.length) {
      var emptyRow = node('tr');
      var cell = node('td', 'Không có bản ghi', 'empty');
      cell.colSpan = 8;
      emptyRow.appendChild(cell);
      body.appendChild(emptyRow);
    }
    traceEntries.slice(tracePage * PAGE_SIZE, (tracePage + 1) * PAGE_SIZE).forEach(function (item) {
      var row = node('tr');
      [formatTime(item.at), item.source, item.what, item.result, item.ip,
        [item.method, item.path].filter(Boolean).join(' '), item.requestId, item.detail].forEach(function (value, index) {
        row.appendChild(node('td', value === null || value === undefined ? '' : value, index === 5 ? 'wrap' : ''));
      });
      body.appendChild(row);
    });
    renderPager(pages);
  }

  function pagerButton(text, label, number, cls) {
    var button = node('button', text, cls);
    button.type = 'button';
    button.setAttribute('aria-label', label);
    button.addEventListener('click', goTo.bind(null, number));
    return button;
  }

  // At most PAGER_SPAN page numbers around the current one, with "…" where pages are left out.
  function renderPager(pages) {
    var pager = document.getElementById('trace-pager');
    clear(pager);
    pager.hidden = pages <= 1;
    var first = Math.max(0, Math.min(tracePage - Math.floor(PAGER_SPAN / 2), pages - PAGER_SPAN));
    var end = Math.min(pages, first + PAGER_SPAN);
    var previous = pagerButton('‹', 'Trang trước', tracePage - 1, 'page step');
    previous.disabled = tracePage === 0;
    pager.appendChild(previous);
    if (first > 0) {
      pager.appendChild(node('span', '…', 'gap'));
    }
    for (var number = first; number < end; number++) {
      var button = pagerButton(String(number + 1), 'Trang ' + (number + 1), number,
        number === tracePage ? 'page current' : 'page');
      if (number === tracePage) {
        button.setAttribute('aria-current', 'page');
      }
      pager.appendChild(button);
    }
    if (end < pages) {
      pager.appendChild(node('span', '…', 'gap'));
    }
    var next = pagerButton('›', 'Trang sau', tracePage + 1, 'page step');
    next.disabled = tracePage >= pages - 1;
    pager.appendChild(next);
  }

  function goTo(number) {
    tracePage = number;
    fillTrace();
  }

  function runTrace() {
    var requestId = document.getElementById('trace-request').value.trim();
    var ip = document.getElementById('trace-ip').value.trim();
    var message = document.getElementById('trace-message');
    message.textContent = '';
    var parts = [];
    if (requestId) {
      parts.push('requestId=' + encodeURIComponent(requestId));
    }
    if (ip) {
      parts.push('ip=' + encodeURIComponent(ip));
    }
    fetchData('api/trace' + (parts.length ? '?' + parts.join('&') : '')).then(function (entries) {
      traceEntries = entries;
      tracePage = 0;
      fillTrace();
      message.textContent = entries.length + ' bản ghi, mới nhất trước'
        + (entries.length >= 200 ? ' (chỉ hiện tối đa 200 bản ghi mới nhất)' : '')
        + (parts.length ? '.' : ', không lọc theo mã yêu cầu hay IP.');
    }).catch(function (error) {
      message.textContent = error.message;
    });
  }

  function render() {
    if (!last.summary) {
      return;
    }
    renderBanner(last.summary, last.health);
    renderSummary(last.summary, last.health);
    renderTimeline(last.timeline);
    renderSuspects(last.suspects);
    drawServices(document.getElementById('chart-services'), last.health, last.window);
    renderActivity(last.actions);
  }

  function refresh() {
    if (loading) {
      return;
    }
    loading = true;
    var windowCode = windowSelect.value;
    var updated = document.getElementById('updated');
    Promise.all([
      fetchData('api/health'),
      fetchData('api/summary?window=' + windowCode),
      fetchData(zoom ? 'api/timeline?from=' + encodeURIComponent(new Date(zoom.from).toISOString()) + '&to=' + encodeURIComponent(new Date(zoom.to).toISOString()) : 'api/timeline?window=' + windowCode),
      fetchData('api/suspects?window=' + windowCode),
      fetchData('api/admin-actions?limit=30')
    ]).then(function (results) {
      last = { health: results[0], summary: results[1], timeline: results[2], suspects: results[3], actions: results[4], window: windowCode };
      render();
      animateNext = false;
      updated.textContent = 'Cập nhật ' + hourFormat.format(new Date());
    }).catch(function () {
      updated.textContent = 'Không tải được dữ liệu';
    }).then(function () {
      loading = false;
    });
  }

  function setZoom(from, to) {
    zoom = from === null ? null : { from: from, to: to };
    animateNext = true;
    refresh();
  }

  var resizeTimer = null;
  window.addEventListener('resize', function () {
    window.clearTimeout(resizeTimer);
    resizeTimer = window.setTimeout(render, 150);
  });
  windowSelect.addEventListener('change', function () {
    zoom = null;
    animateNext = true;
    refresh();
  });
  document.querySelectorAll('.zoom-reset').forEach(function (button) {
    button.addEventListener('click', function () { setZoom(null); });
  });
  document.getElementById('tech').addEventListener('toggle', function (event) {
    if (event.target.open && !traceEntries.length) {
      runTrace();
    }
  });
  document.getElementById('trace-form').addEventListener('submit', function (event) {
    event.preventDefault();
    runTrace();
  });
  refresh();
  window.setInterval(refresh, REFRESH_MS);
})();
