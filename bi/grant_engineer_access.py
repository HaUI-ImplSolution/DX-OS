"""Cho role DXOS Engineer quyền đọc mọi dataset đã đăng ký, để kỹ sư (Gamma) thấy dashboard của app.
Chạy bằng ngữ cảnh ứng dụng của Superset sau bootstrap_datasources.py; idempotent.
"""

from superset.app import create_app

ROLE_NAME = "DXOS Engineer"


def main() -> None:
    app = create_app()
    with app.app_context():
        from superset import db, security_manager
        from superset.connectors.sqla.models import SqlaTable

        role = security_manager.find_role(ROLE_NAME) or security_manager.add_role(ROLE_NAME)
        granted = 0
        for table in db.session.query(SqlaTable).all():
            view_menu = security_manager.find_permission_view_menu("datasource_access", table.get_perm())
            if view_menu is not None and view_menu not in role.permissions:
                security_manager.add_permission_role(role, view_menu)
                granted += 1
        print(f"[bootstrap] role '{ROLE_NAME}': thêm quyền đọc {granted} dataset")


if __name__ == "__main__":
    main()
