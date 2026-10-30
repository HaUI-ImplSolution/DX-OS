// SPDX-License-Identifier: Apache-2.0
import type { Page } from "../types";

interface PaginationProps {
  page: Page<unknown>;
  onChange: (page: number) => void;
  noun?: string;
}

export default function Pagination({ page, onChange, noun = "yêu cầu" }: PaginationProps) {
  if (page.totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button className="btn" disabled={page.page === 0} onClick={() => onChange(page.page - 1)}>
        ‹ Trước
      </button>
      <span>
        Trang {page.page + 1} / {page.totalPages} ({page.totalElements} {noun})
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
