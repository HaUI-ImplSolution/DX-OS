// SPDX-License-Identifier: Apache-2.0
import type { ReactNode } from "react";

interface SectionProps {
  step: string;
  title: string;
  subtitle?: string;
  children: ReactNode;
  disabled?: boolean;
}

export default function Section({ step, title, subtitle, children, disabled }: SectionProps) {
  return (
    <section className={`section${disabled ? " section-disabled" : ""}`}>
      <header className="section-header">
        <span className="section-step">{step}</span>
        <div>
          <h2>{title}</h2>
          {subtitle && <p className="section-subtitle">{subtitle}</p>}
        </div>
      </header>
      <div className="section-body">{children}</div>
    </section>
  );
}
