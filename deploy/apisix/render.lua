-- SPDX-License-Identifier: Apache-2.0
-- Validates the core and installation routes before replacing the standalone configuration.
require("apisix.core.profile").apisix_home = "/usr/local/apisix/"
local yaml = require("lyaml")
local events = require("yaml")
local json = require("dkjson")
local schema = require("apisix.schema_def")
local validate = require("jsonschema").generate_validator
local lfs = require("lfs")

local public = {
    ["api-system-status"] = "status before sign-in",
    ["api-preflight"] = "CORS preflight carries no token",
    ["api-docs"] = "OpenAPI and Swagger UI",
    ["keycloak-login"] = "Keycloak signs callers in",
    keycloak = "Keycloak authenticates its own pages",
    ["keycloak-admin"] = "Admin Console explicitly enabled",
    ["keycloak-admin-closed"] = "Admin Console closed by default",
    ["swagger-ui-removed"] = "Swagger UI is not served by the core",
    photos = "presigned storage links",
    ["portal-root"] = "static portal redirect",
    portal = "static portal",
    ["monitor-ingest"] = "blocked delivery path",
    monitor = "monitor authenticates its own pages",
    web = "static web client",
}

local function fail(message)
    error(message, 0)
end

local function read(path, optional)
    local file, _, code = io.open(path, "rb")
    if not file then
        if optional and code == 2 then return nil end
        fail(path .. ": cannot read file")
    end
    local content = file:read("*a")
    file:close()
    if not content then fail(path .. ": cannot read file") end
    if #content > 1024 * 1024 then fail(path .. ": file exceeds 1 MiB") end
    return content
end

-- The event stream preserves duplicate keys and the distinction between {} and [].
local function parse(path, optional, transform)
    local content = read(path, optional)
    if not content then return {}, {kind = "map", children = {}} end
    if transform then content = transform(content) end
    local ok, data, shape = pcall(function()
        local parser = events.parser(content)
        local function next_event() return assert(parser()) end
        local node
        node = function(event, depth)
            if depth > 32 then fail(path .. ": YAML nesting exceeds 32") end
            if event.anchor or event.type == "ALIAS" then
                fail(path .. ": YAML anchors and aliases are not supported")
            end
            if event.type == "SCALAR" then return {kind = "scalar", value = event.value} end
            local mapping = event.type == "MAPPING_START"
            if not mapping and event.type ~= "SEQUENCE_START" then fail(path .. ": invalid YAML node") end
            local result = {kind = mapping and "map" or "list", children = {}}
            while true do
                local child = next_event()
                if child.type == (mapping and "MAPPING_END" or "SEQUENCE_END") then break end
                if mapping then
                    if child.type ~= "SCALAR" or child.value == "<<" then
                        fail(path .. ": mapping keys must be plain names")
                    end
                    if result.children[child.value] then fail(path .. ": duplicate YAML key") end
                    result.children[child.value] = node(next_event(), depth + 1)
                else
                    result.children[#result.children + 1] = node(child, depth + 1)
                end
            end
            return result
        end
        assert(next_event().type == "STREAM_START")
        assert(next_event().type == "DOCUMENT_START")
        local root = node(next_event(), 0)
        assert(next_event().type == "DOCUMENT_END")
        if next_event().type ~= "STREAM_END" then fail(path .. ": only one YAML document is allowed") end
        if root.kind ~= "map" then fail(path .. ": expected a mapping") end
        local function mark(value, node)
            if value == yaml.null then return json.null end
            if type(value) == "table" then
                setmetatable(value, {__jsontype = node.kind == "list" and "array" or "object"})
                for key, item in pairs(value) do
                    if node.kind == "map" and type(key) ~= "string" then fail(path .. ": mapping keys must be strings") end
                    value[key] = mark(item, assert(node.children[key]))
                end
            end
            return value
        end
        return mark(yaml.load(content), root), root
    end)
    if not ok then
        if type(data) == "string" and data:sub(1, #path + 1) == path .. ":" then fail(data) end
        fail(path .. ": invalid YAML")
    end
    return data, shape
end

local function list(data, shape, key, path)
    if data[key] == nil then data[key] = {} end
    if shape.children[key] and shape.children[key].kind ~= "list" then
        fail(path .. ": " .. key .. " must be a list")
    end
    return data[key]
end

local function id(value, label)
    if not ((type(value) == "string" and #value > 0 and #value <= 64
        and value:match("^[%w_.-]+$")) or
        (type(value) == "number" and value >= 1 and value % 1 == 0)) then
        fail(label .. ": invalid or missing id")
    end
    return tostring(value)
end

local validators, plugins = {}, {}
local function copy(value)
    if type(value) ~= "table" or value == json.null then return value end
    local result = {}
    for key, item in pairs(value) do result[key] = copy(item) end
    return result
end

local function check_upstream(upstream, label)
    local algorithm = upstream.type or "roundrobin"
    if not algorithm:match("^[a-z_]+$") or
        not lfs.attributes("/usr/local/apisix/apisix/balancer/" .. algorithm .. ".lua", "mode") then
        fail(label .. ": unavailable upstream balancer")
    end
    local ok, valid = pcall(require("apisix.upstream").check_upstream_conf, copy(upstream))
    if not ok or not valid then fail(label .. ": invalid upstream configuration") end
end

local function check(resource, kind)
    local label = kind .. " " .. id(resource.id, kind)
    validators[kind] = validators[kind] or validate(assert(schema[kind]))
    if not validators[kind](resource) then fail(label .. ": invalid APISIX schema") end
    if kind == "upstream" then check_upstream(resource, label) end
    if resource.upstream then check_upstream(resource.upstream, label) end
    for name, config in pairs(resource.plugins or {}) do
        if not name:match("^[a-z][a-z0-9-]*$") then fail(label .. ": invalid plugin name") end
        if not plugins[name] then
            local ok, plugin = pcall(require, "apisix.plugins." .. name)
            if not ok then fail(label .. ": unavailable plugin " .. name) end
            plugin.schema.properties = plugin.schema.properties or {}
            plugin.schema.properties._meta = schema.plugin_injected_schema._meta
            plugins[name] = plugin
        end
        local ok, valid = pcall(plugins[name].check_schema, copy(config))
        if not ok or not valid then fail(label .. ": invalid plugin schema for " .. name) end
    end
end

-- Intersects path automata: literal characters, nonempty :segments and a final *.
local function matcher(path)
    if type(path) ~= "string" or path:sub(1, 1) ~= "/" or path:find("[%%?#\\%c]")
        or path:find("//", 1, true) or path:match("/%.[./]") or path:match("/%.%.?$") then
        fail("route: unsupported path syntax")
    end
    local tokens, pos = {}, 1
    while pos <= #path do
        local char = path:sub(pos, pos)
        if char == "*" then
            if pos ~= #path then fail("route: * is only allowed at the end of a path") end
            tokens[#tokens + 1] = "any"
        elseif char == ":" then
            local name = path:sub(pos):match("^:([%a_][%w_]*)")
            local after = name and path:sub(pos + #name + 1, pos + #name + 1)
            if not name or path:sub(pos - 1, pos - 1) ~= "/" or (after ~= "" and after ~= "/") then
                fail("route: unsupported path parameter")
            end
            tokens[#tokens + 1], tokens[#tokens + 2] = "one", "many"
            pos = pos + #name
        else
            tokens[#tokens + 1] = char
        end
        pos = pos + 1
    end
    return tokens
end

local function overlaps(a, b)
    local queue, seen, cursor = {{1, 1}}, {}, 1
    local function push(i, j)
        local key = i .. ":" .. j
        if not seen[key] then seen[key] = true; queue[#queue + 1] = {i, j} end
    end
    while cursor <= #queue do
        local i, j = queue[cursor][1], queue[cursor][2]
        cursor = cursor + 1
        local x, y = a[i], b[j]
        if not x and not y then return true end
        if x == "any" or x == "many" then push(i + 1, j) end
        if y == "any" or y == "many" then push(i, j + 1) end
        if x and y then
            local wx = x == "any" or x == "one" or x == "many"
            local wy = y == "any" or y == "one" or y == "many"
            if x == y or (wx and wy) or (wx and (x == "any" or y ~= "/"))
                or (wy and (y == "any" or x ~= "/")) then
                push((x == "any" or x == "many") and i or i + 1,
                     (y == "any" or y == "many") and j or j + 1)
            end
        end
    end
    return false
end

local function checked(route, configs)
    local effective = {}
    local inherited = configs[tostring(route.plugin_config_id)]
    for name, config in pairs(inherited and inherited.plugins or {}) do effective[name] = config end
    for name, config in pairs(route.plugins or {}) do effective[name] = config end
    local auth = effective["openid-connect"]
    return type(auth) == "table" and auth.unauth_action ~= "pass"
        and not (auth._meta and (auth._meta.disable or auth._meta.filter))
end

-- Keep the parent template's tier and Admin Console blocks for the selected deployment.
local function select_blocks(content)
    local mode = os.getenv("DXOS_MODE") or "full"
    local console = os.getenv("KEYCLOAK_ADMIN_CONSOLE") or "false"
    if mode ~= "full" and mode ~= "core" then fail("DXOS_MODE must be full or core") end
    if console ~= "true" and console ~= "false" then fail("KEYCLOAK_ADMIN_CONSOLE must be true or false") end
    local excluded = { ["app-tier"] = mode == "core",
        ["admin-console-on"] = console ~= "true", ["admin-console-off"] = console == "true" }
    local lines, block = {}, nil
    for line in (content .. "\n"):gmatch("([^\n]*)\n") do
        local name, edge = line:match("^%s*# ([%w-]+):([%a]+)%s*$")
        if excluded[name] ~= nil then
            if edge == "begin" and not block then block = name
            elseif edge == "end" and block == name then block = nil
            else fail("common.yaml: invalid deployment block") end
        elseif not block or not excluded[block] then
            lines[#lines + 1] = line
        end
    end
    if block then fail("common.yaml: unclosed deployment block") end
    return table.concat(lines, "\n")
end

local function build(source, local_path, check_only)
    local reserved, reserved_shape = parse(source .. "/common.yaml")
    local core, core_shape = parse(source .. "/common.yaml", false, select_blocks)
    local extra, extra_shape = parse(local_path, true)
    for key in pairs(extra) do
        if key ~= "routes" and key ~= "upstreams" and key ~= "public_routes" then
            fail(local_path .. ": only routes, upstreams and public_routes are allowed")
        end
    end
    local route_lists = {list(core, core_shape, "routes", source), list(extra, extra_shape, "routes", local_path)}
    local upstream_lists = {list(core, core_shape, "upstreams", source), list(extra, extra_shape, "upstreams", local_path)}
    local exceptions = extra.public_routes or {}
    if extra_shape.children.public_routes and extra_shape.children.public_routes.kind ~= "map" then
        fail(local_path .. ": public_routes must map route ids to reasons")
    end
    for key, reason in pairs(exceptions) do
        id(key, "public_routes")
        if type(reason) ~= "string" or not reason:match("%S") then fail("public_routes: a reason is required") end
    end
    local reserved_ids, reserved_names, paths = {}, {}, {}
    for _, kind in ipairs({"route", "upstream"}) do
        reserved_ids[kind], reserved_names[kind] = {}, {}
        for _, resource in ipairs(list(reserved, reserved_shape, kind .. "s", source)) do
            local key = id(resource.id, kind)
            reserved_ids[kind][key] = true
            if resource.name then reserved_names[kind][resource.name] = true end
            if kind == "route" then
                for _, path in ipairs(resource.uris or {resource.uri}) do
                    paths[#paths + 1] = {id = key, pattern = matcher(path), fallback = key == "web" and path == "/*"}
                end
            end
        end
    end
    local ids, names, configs = {}, {}, {}
    for _, kind in ipairs({"upstream", "plugin_config", "global_rule"}) do
        ids[kind], names[kind] = {}, {}
        local groups = kind == "upstream" and upstream_lists or {list(core, core_shape, kind .. "s", source)}
        for group, items in ipairs(groups) do
            for _, item in ipairs(items) do
                if type(item) ~= "table" then fail(kind .. ": expected a mapping") end
                local key = id(item.id, kind)
                if group == 2 and (reserved_ids[kind][key] or (item.name and reserved_names[kind][item.name])) then
                    fail(kind .. " " .. key .. ": conflicts with a core resource")
                end
                if ids[kind][key] then fail(kind .. " " .. key .. ": duplicate id") end
                if item.name and names[kind][item.name] then fail(kind .. ": duplicate name") end
                if item.name then names[kind][item.name] = true end
                ids[kind][key] = true
                check(item, kind)
                if kind == "plugin_config" then configs[key] = item end
            end
        end
    end
    local route_ids, route_names, local_ids = {}, {}, {}
    for group, routes in ipairs(route_lists) do
        for _, route in ipairs(routes) do
            if type(route) ~= "table" then fail("route: expected a mapping") end
            local key = id(route.id, "route")
            if group == 2 and (reserved_ids.route[key] or (route.name and reserved_names.route[route.name])) then
                fail("route " .. key .. ": conflicts with a core resource")
            end
            if route_ids[key] then fail("route " .. key .. ": duplicate id") end
            route_ids[key] = true
            if route.name and route_names[route.name] then fail("route " .. key .. ": duplicate name") end
            if route.name then route_names[route.name] = true end
            check(route, "route")
            if route.upstream_id and not ids.upstream[tostring(route.upstream_id)] then
                fail("route " .. key .. ": unknown upstream_id")
            end
            if route.plugin_config_id and not configs[tostring(route.plugin_config_id)] then
                fail("route " .. key .. ": unknown plugin_config_id")
            end
            if group == 2 and route.service_id then fail("route " .. key .. ": service_id is not supported in local routes") end
            local protected = checked(route, configs)
            if group == 1 then
                if protected == not not public[key] then fail("route " .. key .. ": core public list does not match authentication") end
            else
                local_ids[key] = true
                if protected == not not exceptions[key] then fail("route " .. key .. ": authentication or an explicit public reason is required") end
                if (route.priority or 0) <= -10 then fail("route " .. key .. ": priority must exceed the web fallback") end
            end
            for _, path in ipairs(route.uris or {route.uri}) do
                local pattern = matcher(path)
                if group == 2 then
                    for _, before in ipairs(paths) do
                        if not before.fallback and overlaps(pattern, before.pattern) then
                            fail("route " .. key .. ": path conflicts with route " .. before.id)
                        end
                    end
                end
                if group == 2 then paths[#paths + 1] = {id = key, pattern = pattern} end
            end
        end
    end
    for key in pairs(public) do if not reserved_ids.route[key] then fail("core public list: missing route " .. key) end end
    for key in pairs(exceptions) do if not local_ids[tostring(key)] then fail("public_routes: unknown local route id") end end
    for _, item in ipairs(upstream_lists[2]) do core.upstreams[#core.upstreams + 1] = item end
    for _, item in ipairs(route_lists[2]) do core.routes[#core.routes + 1] = item end
    local key = check_only and "route-check" or os.getenv("MONITOR_INGEST_KEY")
    if not key or key == "" or key:find("[%c]") then fail("MONITOR_INGEST_KEY is missing or invalid") end
    for _, rule in ipairs(core.global_rules or {}) do
        local logger = rule.plugins and rule.plugins["http-logger"]
        if logger and logger.auth_header == "Monitor __MONITOR_INGEST_KEY__" then
            logger.auth_header = "Monitor " .. key
        end
    end
    local keys, order = {}, {}
    local function collect(value)
        if type(value) ~= "table" then return end
        for key, item in pairs(value) do
            if type(key) == "string" and not keys[key] then keys[key] = true; order[#order + 1] = key end
            collect(item)
        end
    end
    collect(core)
    table.sort(order)
    -- JSON is a YAML subset; sorting mapping keys keeps the generated file stable.
    return json.encode(core, {keyorder = order, indent = true}) .. "\n#END\n", #core.routes
end

local function main()
    local source, local_path, output, check_only = "/src", nil, "/out/apisix.yaml", false
    local i = 1
    while i <= #arg do
        if arg[i] == "--check" then check_only = true
        elseif arg[i] == "--source" or arg[i] == "--local" or arg[i] == "--output" then
            local option = arg[i]
            i = i + 1
            if not arg[i] then fail("missing option value") end
            if option == "--source" then source = arg[i]
            elseif option == "--local" then local_path = arg[i]
            else output = arg[i] end
        else fail("unknown option") end
        i = i + 1
    end
    local content, count = build(source, local_path or source .. "/local.yaml", check_only)
    if not check_only then
        local lock = assert(io.open(output .. ".lock", "a"), "cannot open gateway configuration lock")
        if not lfs.lock(lock, "w") then lock:close(); fail("another gateway configuration render is running") end
        if read(output, true) ~= content then
            local temporary = output .. ".tmp." .. ngx.worker.pid()
            local file = assert(io.open(temporary, "wb"), "cannot create temporary gateway configuration")
            local ok = file:write(content)
            local closed = file:close()
            if not ok or not closed then os.remove(temporary); fail("cannot write gateway configuration") end
            local modified = lfs.attributes(output, "modification") or 0
            local timestamp = math.max(os.time(), modified + 1)
            assert(lfs.touch(temporary, timestamp, timestamp))
            local renamed = os.rename(temporary, output)
            if not renamed then os.remove(temporary); fail("cannot replace gateway configuration") end
        end
        lfs.unlock(lock)
        lock:close()
    end
    print(count .. " routes validated" .. (check_only and "" or "; gateway configuration ready"))
end

local ok, err = pcall(main)
if not ok then io.stderr:write(tostring(err), "\n"); os.exit(1) end
