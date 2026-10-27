export interface PassageField {
  label: string;
  value: string;
}

const REPEATED_LINE = /^(Mã bệnh|Tên tiếng Việt):.*$/;
const BACKEND_SENTENCE = / Tên thuốc, liều và PHI phải do Backend.*$/;
const FIELD_LINE = /^([^:]{2,40}):\s+(.+)$/;

// A disease passage drops the code and name lines that repeat its title and the backend-facing
// sentence of its legal note.
export function passageLines(content: string): string[] {
  return content
    .split("\n")
    .filter((line) => line.trim() && !REPEATED_LINE.test(line))
    .map((line) => line.replace(BACKEND_SENTENCE, ""));
}

// "Label: value" lines become fields; a passage with any other line has no fields.
export function passageFields(lines: string[]): PassageField[] | null {
  const fields = lines.map((line) => FIELD_LINE.exec(line));
  if (lines.length === 0 || fields.some((field) => !field)) return null;
  return fields.map((field) => ({ label: field![1], value: field![2] }));
}
