export default function PentagonRadar({ data }: { data: { metric: string; value: number }[] }) {
  const cx = 80; const cy = 80; const r = 60;
  const n = data.length;
  const angle = (i: number) => (Math.PI * 2 * i) / n - Math.PI / 2;
  const pt = (i: number, radius: number) => ({ x: cx + radius * Math.cos(angle(i)), y: cy + radius * Math.sin(angle(i)) });
  const gridLevels = [0.25, 0.5, 0.75, 1];
  const toPath = (pts: { x: number; y: number }[]) => pts.map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(" ") + "Z";
  const outerPts = data.map((_, i) => pt(i, r));
  const dataPts = data.map((d, i) => pt(i, r * (d.value / 100)));
  return (
    <svg width={160} height={160} viewBox="0 0 160 160" role="img" aria-label={`영역별 텍스트 일치율: ${data.map((d) => `${d.metric} ${d.value}%`).join(", ")}`}>
      {gridLevels.map((lvl) => <path key={lvl} d={toPath(data.map((_, i) => pt(i, r * lvl)))} fill="none" stroke="#e5e7eb" strokeWidth="1" />)}
      {outerPts.map((p, i) => <line key={i} x1={cx} y1={cy} x2={p.x} y2={p.y} stroke="#e5e7eb" strokeWidth="1" />)}
      <path d={toPath(dataPts)} fill="var(--brand-primary)" fillOpacity={0.18} stroke="var(--brand-primary)" strokeWidth="1.8" />
      {data.map((d, i) => {
        const lp = pt(i, r + 14);
        return <text key={i} x={lp.x} y={lp.y} textAnchor="middle" dominantBaseline="middle" fontSize="10" fill="#6B7280">{d.metric}</text>;
      })}
    </svg>
  );
}
