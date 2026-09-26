import { formatMinutes, humanize } from './format';

describe('format', () => {
  it('formats minutes as hours', () => {
    expect(formatMinutes(480)).toBe('8:00 h');
    expect(formatMinutes(1920)).toBe('32:00 h');
    expect(formatMinutes(-95)).toBe('-1:35 h');
    expect(formatMinutes(0)).toBe('0:00 h');
  });

  it('humanizes enum codes', () => {
    expect(humanize('STUDENT_ASSISTANT')).toBe('Student assistant');
    expect(humanize('FRIDAY')).toBe('Friday');
  });
});
