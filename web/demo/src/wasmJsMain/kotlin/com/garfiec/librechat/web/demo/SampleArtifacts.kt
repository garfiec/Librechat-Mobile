package com.garfiec.librechat.web.demo

// Sample chats whose replies build an app (an artifact), which opens live in the artifact panel
// (ArtifactFrame). Apps keep their state in memory: the sandboxed page has no storage, alerts or
// popups. They import only what the app vendors (feature/chat's files/web), as a real reply must.

internal val StashTracker = listOf(
    SampleTurn(
        ask = "Build me an app to keep track of the chocolate stash. My agent keeps buying more and I've lost count.",
        thinking = """
            A small React app: stock per flavour, an Eat button, and a way to see what the agent's habit costs.
            A bar chart of what's left, and the budget front and center, since that's the real problem.
        """.trimIndent(),
        answer = withArtifact(
            before = "Here's a stash tracker. Tap **Eat one** as bars disappear; **Agent buys one** shows what the " +
                "habit costs against its budget.",
            artifact = reactArtifact("chocolate-stash", "Chocolate stash tracker", StashTrackerSource),
            after = "The budget bar turns red once the agent is over. Which, at 40 bars, it already is.",
        ),
    ),
)

internal val SolarSystem = listOf(
    SampleTurn(
        ask = "Maya's science fair project is the solar system. Can you make something she can spin around and show the class?",
        thinking = """
            A 3D model with three.js: the sun, the eight planets on their orbits, Saturn's ring. Drag to turn
            it, a speed slider, and tapping a planet's name shows one fact a kid can say out loud.
        """.trimIndent(),
        answer = withArtifact(
            before = "Here's a solar system Maya can spin with a finger. The planets orbit at their real relative " +
                "speeds (just much faster), and the buttons show one fact about each.",
            artifact = reactArtifact("solar-system", "Solar system", SolarSystemSource),
            after = "Sizes and distances aren't to scale: at real scale the planets would be specks too small to see.",
        ),
    ),
)

internal val ShoppingRules = listOf(
    SampleTurn(
        ask = "Draw me a flowchart of the rules my agent should follow before it buys anything.",
        answer = "Here are the rules as a flowchart. The loop at the end is the one you described: it " +
            "apologises, then negotiates.\n\n```mermaid\n$ShoppingRulesDiagram\n```\n\n" +
            "Put the **Ask me first** step in its instructions word for word. Agents follow a short, " +
            "specific rule much better than a general \"be sensible\".",
    ),
)

internal val ShoppingRulesDiagram get() = """
flowchart LR
    A[Agent finds a deal] --> B{Chocolate?}
    B -- No --> C[Buy it if it's on the list]
    B -- Yes --> D{Under 20 dollars?}
    D -- No --> G[Ask me first]
    D -- Yes --> E{Under 3 this week?}
    E -- Yes --> F[Buy one bar]
    E -- No --> G
    G --> H[I say no]
    H --> I[Agent apologises]
    I --> J[Agent negotiates]
    J --> G
""".trim()

internal fun withArtifact(before: String, artifact: String, after: String) = "$before\n\n$artifact\n\n$after"

internal fun reactArtifact(identifier: String, title: String, source: String) =
    ":::artifact{identifier=\"$identifier\" type=\"application/vnd.react\" title=\"$title\"}\n```jsx\n$source\n```\n:::"

internal val StashTrackerSource get() = """
import { useState } from "react";
import { BarChart, Bar, XAxis, YAxis, ResponsiveContainer, Cell, LabelList } from "recharts";
import { Cookie, ShoppingCart, PiggyBank } from "lucide-react";

const PRICE = 9;
const BUDGET = 100;

const START = [
  { name: "Pistachio", left: 18, eaten: 0, color: "#65a30d" },
  { name: "Dark", left: 10, eaten: 0, color: "#92400e" },
  { name: "Milk", left: 7, eaten: 0, color: "#d97706" },
  { name: "Ruby", left: 5, eaten: 0, color: "#e11d48" },
];

export default function App() {
  const [bars, setBars] = useState(START);
  const [bought, setBought] = useState(40);

  const inStash = bars.reduce((n, b) => n + b.left, 0);
  const eaten = bars.reduce((n, b) => n + b.eaten, 0);
  const spent = bought * PRICE;
  const over = spent > BUDGET;

  const change = (i, f) => setBars(bars.map((b, j) => (j === i ? f(b) : b)));
  const eat = (i) => change(i, (b) => (b.left > 0 ? { ...b, left: b.left - 1, eaten: b.eaten + 1 } : b));
  const buy = (i) => { change(i, (b) => ({ ...b, left: b.left + 1 })); setBought(bought + 1); };

  return (
    <div className="p-5 max-w-xl mx-auto space-y-5 font-sans">
      <div className="flex items-center gap-3">
        <Cookie className="w-8 h-8 text-amber-500" />
        <h1 className="text-2xl font-bold">Chocolate stash</h1>
      </div>

      <div className="grid grid-cols-3 gap-3 text-center">
        <Stat label="In the stash" value={inStash} />
        <Stat label="Eaten" value={eaten} />
        <Stat label="Bought by agent" value={bought} />
      </div>

      <div className="rounded-2xl p-4 bg-amber-500/10">
        <div className="flex items-center gap-2 font-semibold">
          <PiggyBank className="w-5 h-5" /> Agent's budget
        </div>
        <div className="mt-2 h-3 rounded-full bg-black/10 overflow-hidden">
          <div
            className={"h-full rounded-full " + (over ? "bg-rose-500" : "bg-emerald-500")}
            style={{ width: Math.min(100, (spent / BUDGET) * 100) + "%" }}
          />
        </div>
        <div className={"mt-2 text-sm " + (over ? "text-rose-500 font-semibold" : "opacity-70")}>
          {"$" + spent} of {"$" + BUDGET}
          {over ? " · over by $" + (spent - BUDGET) + ". Have a word with your agent." : ""}
        </div>
      </div>

      <div className="h-48">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={bars} margin={{ top: 20, right: 8, left: -20, bottom: 0 }}>
            <XAxis dataKey="name" tick={{ fill: "currentColor", fontSize: 12 }} axisLine={false} tickLine={false} />
            <YAxis allowDecimals={false} tick={{ fill: "currentColor", fontSize: 12 }} axisLine={false} tickLine={false} />
            <Bar dataKey="left" radius={[8, 8, 0, 0]} isAnimationActive={true}>
              {bars.map((b) => <Cell key={b.name} fill={b.color} />)}
              <LabelList dataKey="left" position="top" fill="currentColor" fontSize={12} />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="space-y-2">
        {bars.map((b, i) => (
          <div key={b.name} className="flex items-center gap-3 rounded-xl p-3 bg-black/5">
            <span className="w-3 h-3 rounded-full" style={{ background: b.color }} />
            <span className="flex-1 font-medium">{b.name}</span>
            <button
              onClick={() => eat(i)}
              disabled={b.left === 0}
              className="px-3 py-1.5 rounded-lg bg-amber-600 text-white text-sm disabled:opacity-40 active:scale-95 transition"
            >
              Eat one
            </button>
            <button
              onClick={() => buy(i)}
              className="px-3 py-1.5 rounded-lg border border-amber-600 text-amber-600 text-sm flex items-center gap-1 active:scale-95 transition"
            >
              <ShoppingCart className="w-4 h-4" /> Agent buys one
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

function Stat({ label, value }) {
  return (
    <div className="rounded-2xl p-3 bg-black/5">
      <div className="text-2xl font-bold">{value}</div>
      <div className="text-xs opacity-70">{label}</div>
    </div>
  );
}
""".trim()

internal val SolarSystemSource get() = """
import { useEffect, useRef, useState } from "react";
import {
  Scene, PerspectiveCamera, WebGLRenderer, SphereGeometry, RingGeometry, MeshStandardMaterial,
  MeshBasicMaterial, Mesh, PointLight, AmbientLight, Group, BufferGeometry, Float32BufferAttribute,
  Points, PointsMaterial, LineLoop, LineBasicMaterial, DoubleSide,
} from "three";

const PLANETS = [
  { name: "Mercury", color: 0x9ca3af, size: 0.35, distance: 4, period: 0.24, fact: "A year on Mercury is only 88 days." },
  { name: "Venus", color: 0xfbbf24, size: 0.6, distance: 5.6, period: 0.62, fact: "Venus is the hottest planet, hotter than Mercury." },
  { name: "Earth", color: 0x3b82f6, size: 0.65, distance: 7.4, period: 1, fact: "Earth is the only planet known to have life." },
  { name: "Mars", color: 0xef4444, size: 0.45, distance: 9.2, period: 1.88, fact: "Mars has the tallest volcano in the solar system." },
  { name: "Jupiter", color: 0xd6a36b, size: 1.5, distance: 12.5, period: 11.9, fact: "Jupiter is so big all the other planets would fit inside it." },
  { name: "Saturn", color: 0xeab308, size: 1.25, distance: 16, period: 29.5, ring: true, fact: "Saturn could float in a giant bathtub." },
  { name: "Uranus", color: 0x67e8f9, size: 0.9, distance: 19, period: 84, fact: "Uranus spins on its side, like a rolling ball." },
  { name: "Neptune", color: 0x6366f1, size: 0.88, distance: 21.5, period: 165, fact: "Neptune has the fastest winds of any planet." },
];

export default function App() {
  const mount = useRef(null);
  const speed = useRef(1);
  const [speedShown, setSpeedShown] = useState(1);
  const [picked, setPicked] = useState(PLANETS[2]);

  useEffect(() => {
    const el = mount.current;
    const scene = new Scene();
    const camera = new PerspectiveCamera(50, el.clientWidth / el.clientHeight, 0.1, 500);
    const renderer = new WebGLRenderer({ antialias: true, alpha: true });
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.setSize(el.clientWidth, el.clientHeight);
    el.appendChild(renderer.domElement);

    scene.add(new AmbientLight(0xffffff, 0.35));
    const light = new PointLight(0xffffff, 3, 0, 0);
    scene.add(light);

    const sun = new Mesh(new SphereGeometry(2, 48, 48), new MeshBasicMaterial({ color: 0xfacc15 }));
    scene.add(sun);

    const starPositions = [];
    for (let i = 0; i < 600; i++) {
      const r = 120 + Math.random() * 60, t = Math.random() * Math.PI * 2, p = Math.acos(2 * Math.random() - 1);
      starPositions.push(r * Math.sin(p) * Math.cos(t), r * Math.cos(p), r * Math.sin(p) * Math.sin(t));
    }
    const starGeometry = new BufferGeometry();
    starGeometry.setAttribute("position", new Float32BufferAttribute(starPositions, 3));
    scene.add(new Points(starGeometry, new PointsMaterial({ color: 0xffffff, size: 0.6 })));

    const system = new Group();
    scene.add(system);
    const planets = PLANETS.map((p, i) => {
      const orbit = [];
      for (let a = 0; a < 128; a++) {
        const t = (a / 128) * Math.PI * 2;
        orbit.push(Math.cos(t) * p.distance, 0, Math.sin(t) * p.distance);
      }
      const orbitGeometry = new BufferGeometry();
      orbitGeometry.setAttribute("position", new Float32BufferAttribute(orbit, 3));
      system.add(new LineLoop(orbitGeometry, new LineBasicMaterial({ color: 0x64748b, transparent: true, opacity: 0.35 })));

      const pivot = new Group();
      pivot.rotation.y = i * 1.7;
      const body = new Mesh(new SphereGeometry(p.size, 32, 32), new MeshStandardMaterial({ color: p.color, roughness: 0.8 }));
      body.position.x = p.distance;
      pivot.add(body);
      if (p.ring) {
        const ring = new Mesh(
          new RingGeometry(p.size * 1.4, p.size * 2.2, 64),
          new MeshBasicMaterial({ color: 0xd6c08a, side: DoubleSide, transparent: true, opacity: 0.7 }),
        );
        ring.rotation.x = Math.PI / 2.3;
        body.add(ring);
      }
      system.add(pivot);
      return { pivot, body, period: p.period };
    });

    let yaw = 0.6, pitch = 0.45, dragging = null;
    const place = () => {
      const r = 44 * Math.max(1, 1.2 / camera.aspect);
      camera.position.set(r * Math.cos(pitch) * Math.sin(yaw), r * Math.sin(pitch), r * Math.cos(pitch) * Math.cos(yaw));
      camera.lookAt(0, 0, 0);
    };
    place();
    const down = (e) => { dragging = { x: e.clientX, y: e.clientY }; el.setPointerCapture(e.pointerId); };
    const move = (e) => {
      if (!dragging) return;
      yaw -= (e.clientX - dragging.x) * 0.008;
      pitch = Math.max(-1.2, Math.min(1.2, pitch + (e.clientY - dragging.y) * 0.006));
      dragging = { x: e.clientX, y: e.clientY };
      place();
    };
    const up = () => { dragging = null; };
    el.addEventListener("pointerdown", down);
    el.addEventListener("pointermove", move);
    el.addEventListener("pointerup", up);
    el.addEventListener("pointercancel", up);

    const resize = () => {
      camera.aspect = el.clientWidth / el.clientHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(el.clientWidth, el.clientHeight);
      place();
    };
    const observer = new ResizeObserver(resize);
    observer.observe(el);

    let frame, last = performance.now();
    const tick = (now) => {
      const dt = (now - last) / 1000;
      last = now;
      planets.forEach((p) => {
        p.pivot.rotation.y += (dt * speed.current * 0.6) / p.period;
        p.body.rotation.y += dt;
      });
      renderer.render(scene, camera);
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);

    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      renderer.dispose();
      el.removeChild(renderer.domElement);
    };
  }, []);

  return (
    <div className="flex flex-col font-sans" style={{ height: "100vh" }}>
      <div ref={mount} className="flex-1 min-h-0 touch-none cursor-grab" style={{ background: "#020617" }} />
      <div className="p-4 space-y-3">
        <div className="flex flex-wrap gap-2">
          {PLANETS.map((p) => (
            <button
              key={p.name}
              onClick={() => setPicked(p)}
              className={"px-3 py-1 rounded-full text-sm border transition " + (picked.name === p.name ? "bg-indigo-500 text-white border-indigo-500" : "border-current opacity-70")}
            >
              {p.name}
            </button>
          ))}
        </div>
        <p className="text-sm"><b>{picked.name}:</b> {picked.fact}</p>
        <label className="flex items-center gap-3 text-sm">
          Speed
          <input
            type="range" min="0" max="5" step="0.1" value={speedShown} className="flex-1"
            onChange={(e) => { speed.current = +e.target.value; setSpeedShown(+e.target.value); }}
          />
        </label>
      </div>
    </div>
  );
}
""".trim()
