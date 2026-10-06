import type { Page } from "../types";

interface PaginationProps {
  page: Page<unknown>;
  onChange: (page: number) => void;
}

export default function Pagination({ page, onChange }: PaginationProps) {
  if (page.totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button className="btn" disabled={page.page === 0} onClick={() => onChange(page.page - 1)}>
        ‹ Trước
      </button>
      <span>
        Trang {page.page + 1} / {page.totalPages} ({page.totalElements} yêu cầu)
      </span>
      <button
        className="btn"
        disabled={page.page + 1 >= page.totalPages}
        onClick={() => onChange(page.page + 1)}
      >
        Sau ›
      </button>
    </div>
  );
}
