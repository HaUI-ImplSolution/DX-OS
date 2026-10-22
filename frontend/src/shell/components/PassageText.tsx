import { passageText } from "../labels";

const FIELD_LINE = /^([^:]{2,40}):\s+(.+)$/;

// A passage written as "Label: value" lines reads as a two-column list; any other passage keeps
// its line breaks.
export default function PassageText({ content }: { content: string }) {
  const lines = passageText(content).split("\n").filter(Boolean);
  const fields = lines.map((line) => FIELD_LINE.exec(line));
  if (lines.length === 0 || fields.some((field) => !field)) {
    return <p className="passage-text">{lines.join("\n")}</p>;
  }
  return (
    <dl className="passage-fields">
      {fields.map((field, index) => (
        <div key={index}>
          <dt>{field![1]}</dt>
          <dd>{field![2]}</dd>
        </div>
      ))}
    </dl>
  );
}
