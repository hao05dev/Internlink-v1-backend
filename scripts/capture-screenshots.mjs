// Chụp ảnh màn hình cho README bằng Edge headless qua Chrome DevTools Protocol.
// Yêu cầu: frontend :3000, backend :8080 (và AI :8001) đang chạy với dữ liệu seed demo.
// Chạy (từ repo backend): node scripts/capture-screenshots.mjs assets/screenshots
// Mật khẩu demo đọc từ README backend (tài khoản seed), không ghi vào script.
import { spawn } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";
const BASE = "http://localhost:3000";
const OUT = process.argv[2] ?? "screenshots";
const PORT = 9333;
const readme = readFileSync(new URL("../README.md", import.meta.url), "utf8");
const PASSWORD = readme.match(/mật khẩu chung: \*\*`([^`]+)`\*\*/)?.[1];
if (!PASSWORD) throw new Error("Không tìm thấy mật khẩu demo trong README backend");

// [file, email (null = khách), đường dẫn, tab đăng nhập doanh nghiệp?]
const SHOTS = [
  ["01-home.png", null, "/"],
  ["02-jobs.png", null, "/jobs"],
  ["03-student-dashboard.png", "b2110940@student.ctu.edu.vn", "/student/dashboard"],
  ["04-student-ai.png", "b2110940@student.ctu.edu.vn", "/student/ai"],
  ["05-company-jobs.png", "tuyendung.fptct@fpt.com", "/company/jobs", true],
  ["07-faculty-dashboard.png", "qltt.cict@ctu.edu.vn", "/faculty/dashboard"],
  ["08-faculty-analytics.png", "qltt.cict@ctu.edu.vn", "/faculty/analytics"],
  ["09-lecturer-grading.png", "gvhd.son@ctu.edu.vn", "/lecturer/grading"],
  ["10-mentor-weekly.png", "mentor.nam@fpt.com", "/mentor/weekly-evaluations", true],
];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function connect() {
  for (let i = 0; i < 50; i++) {
    try {
      const targets = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
      const page = targets.find((t) => t.type === "page");
      if (page) return page.webSocketDebuggerUrl;
    } catch {}
    await sleep(200);
  }
  throw new Error("Edge không mở cổng DevTools");
}

class Cdp {
  constructor(url) {
    this.ws = new WebSocket(url);
    this.id = 0;
    this.pending = new Map();
    this.ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result);
      }
    };
  }
  open() {
    return new Promise((resolve) => (this.ws.onopen = resolve));
  }
  send(method, params = {}) {
    const id = ++this.id;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`CDP timeout: ${method}`));
      }, 30000);
      this.pending.set(id, {
        resolve: (value) => (clearTimeout(timer), resolve(value)),
        reject: (error) => (clearTimeout(timer), reject(error)),
      });
    });
  }
  async eval(expression) {
    const { result } = await this.send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
    return result.value;
  }
  async goto(path) {
    await this.send("Page.navigate", { url: BASE + path });
    await this.idle();
  }
  async idle() {
    // Chờ trang tải xong và không còn spinner/skeleton.
    for (let i = 0; i < 60; i++) {
      await sleep(500);
      const ready = await this.eval(
        `document.readyState === "complete" && !document.querySelector('[aria-busy="true"], .animate-pulse, .animate-spin')`,
      );
      if (ready) break;
    }
    await sleep(1200);
    // Ẩn chỉ báo build của Next dev server để ảnh sạch.
    await this.eval(`(() => {
      const s = document.createElement("style");
      s.textContent = "#__next-build-watcher, nextjs-portal, [data-nextjs-toast] { display: none !important; }";
      document.head.appendChild(s);
    })()`);
    await sleep(200);
  }
  async screenshot() {
    for (let i = 0; ; i++) {
      try {
        return (await this.send("Page.captureScreenshot", {
          format: "png",
          captureBeyondViewport: true,
          clip: { x: 0, y: 0, width: 1440, height: 900, scale: 1 },
        })).data;
      } catch (error) {
        if (i >= 3) throw error;
        await sleep(1500);
      }
    }
  }
}

async function login(cdp, email, companyTab) {
  await cdp.send("Network.clearBrowserCookies");
  await cdp.goto("/login");
  if (companyTab) {
    await cdp.eval(`[...document.querySelectorAll("button")].find(b => /Doanh nghiệp/.test(b.textContent) && b.type === "button")?.click()`);
    await sleep(500);
  }
  await cdp.eval(`(() => {
    const set = (el, v) => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set.call(el, v);
      el.dispatchEvent(new Event("input", { bubbles: true }));
    };
    set(document.getElementById("login-email"), ${JSON.stringify(email)});
    set(document.getElementById("login-password"), ${JSON.stringify(PASSWORD)});
    document.querySelector('form button[type="submit"]').click();
  })()`);
  for (let i = 0; i < 40; i++) {
    await sleep(500);
    if (!(await cdp.eval("location.pathname")).startsWith("/login")) return;
  }
  throw new Error(`Đăng nhập thất bại: ${email}`);
}

mkdirSync(OUT, { recursive: true });
// Hồ sơ Edge tạm, tắt trình quản lý mật khẩu để không hiện bong bóng "lưu mật khẩu" đè lên ảnh.
const profile = mkdtempSync(join(tmpdir(), "il-edge-"));
mkdirSync(join(profile, "Default"));
writeFileSync(join(profile, "Default", "Preferences"), JSON.stringify({
  credentials_enable_service: false,
  profile: { password_manager_enabled: false },
  autofill: { profile_enabled: false, credit_card_enabled: false },
}));
const edge = spawn(EDGE, [
  "--headless=new", `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
  "--window-size=1440,1000", "--hide-scrollbars", "--no-first-run", "--disable-save-password-bubble",
  "--disable-features=msEdgeWallet,PasswordManagerOnboarding,AutofillServerCommunication", "about:blank",
]);
try {
  const cdp = new Cdp(await connect());
  await cdp.open();
  await cdp.send("Page.enable");
  await cdp.send("Network.enable");
  // Viewport cao 1000 nhưng chỉ cắt 900 phía trên: Edge headless vẽ một ô "Done" ở góc dưới-trái
  // viewport (không thuộc DOM), cắt như vậy để ô này nằm ngoài ảnh.
  await cdp.send("Emulation.setDeviceMetricsOverride", { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  let current = undefined;
  for (const [file, email, path, companyTab] of SHOTS) {
    if (email !== current) {
      if (email) await login(cdp, email, companyTab);
      else await cdp.send("Network.clearBrowserCookies");
      current = email;
    }
    await cdp.goto(path);
    const data = await cdp.screenshot();
    writeFileSync(join(OUT, file), Buffer.from(data, "base64"));
    console.log(`${file} <- ${await cdp.eval("location.pathname")}`);
  }
} finally {
  edge.kill();
}
