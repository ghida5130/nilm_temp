/**
 * test_ui_real_js.js
 * 
 * 운영 파일(waveform_viewer.html)의 실제 JavaScript 코드를 추출하여 Node.js 환경에서 직접 실행 및 검증합니다.
 * 운영 코드를 테스트 파일에 복제하지 않고 fs.readFileSync로 읽어 vm.runInContext로 실행합니다.
 */

const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');

// 1. 운영 HTML 파일 읽기 및 <script> 추출
const htmlPath = path.join(__dirname, '..', 'waveform_viewer.html');
const htmlContent = fs.readFileSync(htmlPath, 'utf-8');

const scriptMatch = htmlContent.match(/<script>([\s\S]*?)<\/script>/);
if (!scriptMatch) {
  console.error("오류: waveform_viewer.html에서 <script> 태그를 찾을 수 없습니다.");
  process.exit(1);
}
const scriptCode = scriptMatch[1];

// 2. Mock DOM 및 브라우저 환경 구축
function createMockElement(id = '', tagName = 'div') {
  const el = {
    id: id,
    tagName: tagName.toUpperCase(),
    textContent: '',
    innerHTML: '',
    value: '',
    disabled: false,
    checked: true,
    className: '',
    children: [],
    style: {},
    attributes: {},
    setAttribute(name, val) { this.attributes[name] = String(val); },
    getAttribute(name) { return this.attributes[name]; },
    replaceChildren(...newChildren) { this.children = [...newChildren]; },
    appendChild(child) { this.children.push(child); return child; },
    insertBefore(child, ref) {
      const idx = this.children.indexOf(ref);
      if (idx >= 0) this.children.splice(idx, 0, child);
      else this.children.unshift(child);
      return child;
    },
    removeChild(child) {
      const idx = this.children.indexOf(child);
      if (idx >= 0) this.children.splice(idx, 1);
      return child;
    },
    get options() { return this.children; },
    get innerText() { return this.textContent; },
    set innerText(val) { this.textContent = String(val); },
    getContext(type) {
      return { canvas: this, type };
    },
    click() {}
  };
  return el;
}

const domElements = new Map();
function getOrCreateElement(id, tagName = 'div') {
  if (!domElements.has(id)) {
    domElements.set(id, createMockElement(id, tagName));
  }
  return domElements.get(id);
}

// 사전 등록할 필수 DOM 요소들
const preRegisteredIds = [
  'householdTableBody', 'simDateInput', 'observedHouseBadge', 'observedHouseSelect',
  'btnPause', 'badgeStatus', 'valPower', 'valApparent', 'valCurrent', 'valVoltage',
  'valPf', 'valReactive', 'valTime', 'valMaxPower', 'cardPower', 'timelineNotice',
  'eventTableBody', 'serverIndicator', 'chartMain', 'chartSecondary',
  'btnPresetPeak', 'btnPresetMissed', 'btnPresetAllRandom', 'btnPresetManual',
  'btnRunMulti', 'btnResetAll', 'btnManualToggle'
];
preRegisteredIds.forEach(id => getOrCreateElement(id));

// 가구별 DOM 요소 사전 등록 (H001 ~ H010)
const houses = ["H001", "H002", "H003", "H004", "H005", "H006", "H007", "H008", "H009", "H010"];
houses.forEach(h => {
  getOrCreateElement(`row_${h}`);
  getOrCreateElement(`chk_${h}`, 'input');
  getOrCreateElement(`scenario_${h}`, 'select');
  getOrCreateElement(`status_badge_${h}`, 'span');
  getOrCreateElement(`power_val_${h}`, 'span');
});

// 가전 칩 및 상태 라벨 사전 등록
const devices = ['kettle', 'induction', 'iron', 'microwave', 'hair_dryer', 'vacuum_cleaner'];
devices.forEach(d => {
  getOrCreateElement(`chip_${d}`, 'button');
  getOrCreateElement(`state_${d}`, 'span');
});

// Chart Mock 클래스
function MockChart(ctx, config) {
  this.ctx = ctx;
  this.type = config.type;
  this.data = config.data || { labels: [], datasets: [] };
  this.update = function(mode) {};
}

// Fetch Mock 기록
const fetchCalls = [];
let mockFetchResponse = {
  ok: true,
  status: 200,
  json: async () => ({ status: 'success' })
};

async function mockFetch(url, options = {}) {
  fetchCalls.push({ url, options });
  return mockFetchResponse;
}

// EventSource Mock
let mockEventSourceInstances = [];
function MockEventSource(url) {
  this.url = url;
  this.closeCalled = false;
  this.close = function() {
    this.closeCalled = true;
  };
  mockEventSourceInstances.push(this);
}

// Blob & URL Mock
class MockBlob {
  constructor(contentParts, options) {
    this.content = contentParts.join('');
    this.options = options;
  }
}

const mockUrl = {
  createObjectURL: (blob) => `blob:mock-url-${Math.random()}`,
  revokeObjectURL: (url) => {}
};

// Sandbox 환경 구축
const sandbox = {
  console: console,
  setTimeout: setTimeout,
  clearTimeout: clearTimeout,
  setInterval: setInterval,
  clearInterval: clearInterval,
  Date: Date,
  Math: Math,
  JSON: JSON,
  String: String,
  Number: Number,
  Boolean: Boolean,
  Array: Array,
  Map: Map,
  Set: Set,
  Object: Object,
  Error: Error,
  TypeError: TypeError,
  window: {},
  document: {
    getElementById: (id) => getOrCreateElement(id),
    createElement: (tag) => createMockElement('', tag),
    body: createMockElement('body')
  },
  Chart: MockChart,
  EventSource: MockEventSource,
  fetch: mockFetch,
  alert: (msg) => { console.log(`[Mock Alert] ${msg}`); },
  Blob: MockBlob,
  URL: mockUrl,
  navigator: {}
};
sandbox.window = sandbox;

const context = vm.createContext(sandbox);

// 3. 실제 운영 스크립트 실행
try {
  vm.runInContext(scriptCode, context);
  console.log("✔ 운영 JavaScript 코드 vm 로드 및 초기화 성공");
} catch (err) {
  console.error("✘ 운영 JavaScript 코드 실행 실패:", err);
  process.exit(1);
}

const getGlobal = (expr) => vm.runInContext(expr, context);

// ==========================================
// 검증 시나리오 실행
// ==========================================
async function runTests() {
  console.log("\n=== 프런트엔드 운영 실제 JS 동작 검증 시작 ===");

  const processServerMetrics = getGlobal('processServerMetrics');
  const changeObservedHouse = getGlobal('changeObservedHouse');
  const getOrCreateHistory = getGlobal('getOrCreateHistory');
  const getCsvContentForHouse = getGlobal('getCsvContentForHouse');
  const togglePause = getGlobal('togglePause');

  // Test 1: 첫 SSE 이벤트 수신 시 apparentS 처리 및 TypeError 방지 검증
  console.log("\n[Test 1] 첫 SSE 이벤트 처리 및 apparentS 키 정합성 검증");
  const sseDataH001 = {
    house: "H001",
    scenario: "peak",
    sec: 1,
    status: "running",
    totalP: 2150.5,
    totalQ: 120.3,
    apparentS: 2153.9,
    currentA: 9.79,
    pf: 0.998,
    voltage: 220.0,
    simTimeKst: "08:10:01",
    activeNames: ["kettle"],
    eventNoticeText: "08:10 아침 루틴 시작"
  };

  assert.doesNotThrow(() => {
    processServerMetrics(sseDataH001);
  }, "첫 SSE 이벤트 수신 시 TypeError가 발생하지 않아야 합니다.");

  const histH001 = getOrCreateHistory("H001");
  assert.strictEqual(histH001.apparentS.length, 1, "historyByHouse에 apparentS가 1개 기록되어야 합니다.");
  assert.strictEqual(histH001.apparentS[0], 2153.9, "기록된 apparentS 값이 정확해야 합니다.");
  assert.strictEqual(getOrCreateElement('valApparent').textContent, "2153.9", "valApparent DOM에 2153.9가 렌더링되어야 합니다.");
  console.log("✔ Test 1 통과: 첫 SSE 수신 및 apparentS 렌더링 정상 동작");

  // Test 2: 가구별 최고 전력 (maxPowerByHouse) 독립 관리 검증
  console.log("\n[Test 2] 가구별 최고 소비전력 독립 관리 검증");
  const sseDataH002 = {
    house: "H002",
    scenario: "routine_missed",
    sec: 1,
    status: "running",
    totalP: 55.4,
    totalQ: 10.2,
    apparentS: 56.3,
    currentA: 0.25,
    pf: 0.984,
    voltage: 220.0,
    simTimeKst: "08:10:01",
    activeNames: []
  };
  processServerMetrics(sseDataH002);

  // 현재 관찰 가구가 H001일 때 최고 전력
  assert.strictEqual(getOrCreateElement('valMaxPower').textContent, "2150.5 W", "H001 관찰 시 최고 전력은 2150.5 W여야 합니다.");

  // H002로 관찰 가구 전환
  changeObservedHouse("H002");
  assert.strictEqual(getOrCreateElement('valMaxPower').textContent, "55.4 W", "H002 관찰 시 최고 전력은 H001의 2150.5 W가 아닌 55.4 W여야 합니다.");

  // 다시 H001로 전환 시 H001의 최고 전력이 복원되어야 함
  changeObservedHouse("H001");
  assert.strictEqual(getOrCreateElement('valMaxPower').textContent, "2150.5 W", "H001 재전환 시 최고 전력 2150.5 W가 복원되어야 합니다.");
  console.log("✔ Test 2 통과: 가구별 최고 전력 독립 관리 확인");

  // Test 3: 가구 전환 시 차트 및 상태 독립성 검증
  console.log("\n[Test 3] 가구 전환 시 차트 데이터 및 상태 독립성 검증");
  changeObservedHouse("H002");
  const chartMain = getGlobal('chartMain');
  assert.strictEqual(chartMain.data.datasets[0].data[0], 55.4, "차트 유효전력 데이터가 H002의 55.4로 갱신되어야 합니다.");
  assert.strictEqual(chartMain.data.datasets[1].data[0], 56.3, "차트 피상전력 데이터가 H002의 56.3으로 갱신되어야 합니다.");
  assert.strictEqual(getOrCreateElement('valPower').textContent, "55.4", "valPower DOM이 55.4로 갱신되어야 합니다.");
  console.log("✔ Test 3 통과: 가구 전환 시 차트 및 메트릭 분리 확인");

  // Test 4: CSV 생성 및 apparent_power 컬럼 정합성 검증
  console.log("\n[Test 4] getCsvContentForHouse CSV 생성 및 apparent_power 검증");
  const csvH001 = getCsvContentForHouse("H001");
  assert.ok(csvH001 !== null, "H001의 CSV 내용이 생성되어야 합니다.");
  assert.ok(csvH001.includes("apparent_power"), "CSV 헤더에 apparent_power가 포함되어야 합니다.");
  assert.ok(csvH001.includes("2153.9"), "CSV 데이터 행에 H001의 apparentS(2153.9)가 정확히 기록되어야 합니다.");
  assert.ok(!csvH001.includes("undefined"), "CSV에 undefined 값이 포함되어서는 안 됩니다.");

  const csvH002 = getCsvContentForHouse("H002");
  assert.ok(csvH002.includes("56.3"), "H002 CSV에 56.3이 정확히 기록되어야 합니다.");
  console.log("✔ Test 4 통과: CSV 생성 및 apparent_power 정합성 검증 완료");

  // Test 5: togglePause 시 /api/pause 및 /api/resume 호출과 상태 보존 검증
  console.log("\n[Test 5] togglePause 일시정지/재개 API 호출 및 상태 보존 검증");
  fetchCalls.length = 0;
  getGlobal('isServerConnected = true');
  getGlobal('isPaused = false');

  // 일시정지 실행
  await togglePause();
  assert.strictEqual(fetchCalls.length, 1, "API 호출이 1회 발생해야 합니다.");
  assert.strictEqual(fetchCalls[0].url, "/api/pause", "호출 엔드포인트가 /api/pause여야 합니다.");
  assert.strictEqual(getGlobal('isPaused'), true, "isPaused 상태가 true여야 합니다.");
  assert.strictEqual(getOrCreateElement('btnPause').textContent, "재생", "버튼 라벨이 '재생'이어야 합니다.");
  assert.strictEqual(getOrCreateElement('badgeStatus').textContent, "일시정지됨", "뱃지가 '일시정지됨'이어야 합니다.");

  // 일시정지 중에도 기존 히스토리가 그대로 보존되어 있는지 확인
  const histH001Paused = getOrCreateHistory("H001");
  assert.strictEqual(histH001Paused.activeP.length, 1, "일시정지 후에도 H001 히스토리가 보존되어야 합니다.");
  assert.strictEqual(histH001Paused.apparentS[0], 2153.9, "apparentS 값이 유지되어야 합니다.");

  // 재개 실행
  await togglePause();
  assert.strictEqual(fetchCalls.length, 2, "API 호출이 2회 발생해야 합니다.");
  assert.strictEqual(fetchCalls[1].url, "/api/resume", "호출 엔드포인트가 /api/resume여야 합니다.");
  assert.strictEqual(getGlobal('isPaused'), false, "isPaused 상태가 false여야 합니다.");
  assert.strictEqual(getOrCreateElement('btnPause').textContent, "일시정지", "버튼 라벨이 '일시정지'여야 합니다.");
  console.log("✔ Test 5 통과: 실제 pause/resume 엔드포인트 연동 및 상태 보존 확인");

  // Test 6: 운영 HTML 코드 apparentS 키 완전 통일 및 tick() 실행 TypeError 방지 검증
  console.log("\n[Test 6] apparentS 키 완전 통일 및 tick() 실행 검증");
  assert.ok(!htmlContent.includes("historyData.apparentP"), "HTML 내에 historyData.apparentP가 남아있지 않아야 합니다.");
  assert.ok(!htmlContent.includes("hist.apparentP"), "HTML 내에 hist.apparentP가 남아있지 않아야 합니다.");

  // tick() 직접 호출 검증
  const tickFn = getGlobal('tick');
  assert.doesNotThrow(() => {
    tickFn();
  }, "tick() 호출 시 TypeError가 발생하지 않아야 합니다.");

  const globalHistoryData = getGlobal('historyData');
  assert.ok(globalHistoryData.apparentS.length > 0, "tick() 호출 후 historyData.apparentS에 데이터가 적재되어야 합니다.");
  assert.strictEqual(globalHistoryData.apparentP, undefined, "historyData.apparentP 필드는 존재하지 않아야 합니다.");
  console.log("✔ Test 6 통과: apparentS 완전 통일 및 tick() 정상 실행 검증 완료");

  // Test 7: resetSimulation 실패 처리 및 EventSource 유지, UI 보존, startMultiSimulation 중단 검증
  console.log("\n[Test 7] resetSimulation 실패 처리 및 EventSource 유지 검증");

  // 가짜 EventSource 생성 및 등록
  const testEvtSource = new MockEventSource("/api/stream");
  sandbox.testEvtSource = testEvtSource;
  vm.runInContext('evtSource = testEvtSource', context);
  assert.strictEqual(getGlobal('evtSource'), testEvtSource);
  assert.strictEqual(testEvtSource.closeCalled, false);

  // 기존 데이터 준비
  getGlobal('historyByHouse.set("H001", { times: [1], activeP: [100.0], apparentS: [105.0] })');
  getGlobal('activeConfiguredHouseholds = [{ house: "H001", scenario: "peak" }]');
  getGlobal('chartMain.data.datasets[0].data = [100.0]');
  const initialHistorySize = getGlobal('historyByHouse').size;
  assert.ok(initialHistorySize > 0, "테스트 전 historyByHouse에 데이터가 존재해야 합니다.");

  // 1. fetch가 HTTP 500 실패를 반환하도록 설정
  let noticeErrorCalledWith = null;
  context.showNoticeError = (msg) => { noticeErrorCalledWith = msg; };
  mockFetchResponse = {
    ok: false,
    status: 500,
    json: async () => ({ status: "error", message: "시뮬레이터 워커 종료 실패" })
  };

  const resetSimulationFn = getGlobal('resetSimulation');
  fetchCalls.length = 0;

  // resetSimulation 실행
  const resetResultFail = await resetSimulationFn();

  // 검증: 실패 시 false 반환
  assert.strictEqual(resetResultFail, false, "reset 실패 시 resetSimulation은 false를 반환해야 합니다.");

  // 검증: EventSource.close()가 호출되지 않았는지 검증 (사용자 세부 조건 1)
  assert.strictEqual(testEvtSource.closeCalled, false, "reset 실패 시 기존 EventSource를 닫지 않아야 합니다.");
  assert.ok(getGlobal('evtSource') !== null, "reset 실패 시 evtSource 변수가 null이 되지 않아야 합니다.");

  // 검증: 차트, historyByHouse, activeConfiguredHouseholds가 초기화되지 않고 보존
  assert.strictEqual(getGlobal('historyByHouse').size, initialHistorySize, "reset 실패 시 historyByHouse가 유지되어야 합니다.");
  assert.strictEqual(getGlobal('activeConfiguredHouseholds').length, 1, "reset 실패 시 activeConfiguredHouseholds가 유지되어야 합니다.");
  assert.strictEqual(getGlobal('chartMain').data.datasets[0].data.length, 1, "reset 실패 시 차트 데이터가 유지되어야 합니다.");

  // 검증: showNoticeError가 호출되었는지
  assert.ok(noticeErrorCalledWith !== null, "reset 실패 시 showNoticeError가 호출되어야 합니다.");
  assert.ok(noticeErrorCalledWith.includes("오류") || noticeErrorCalledWith.includes("500"), "에러 메시지가 사용자에게 전달되어야 합니다.");

  // 2. startMultiSimulation이 reset 실패 시 /api/start를 호출하지 않고 중단되는지 검증
  fetchCalls.length = 0;
  const startMultiSimulationFn = getGlobal('startMultiSimulation');
  getOrCreateElement('chk_H001', 'input').checked = true;
  await startMultiSimulationFn();

  assert.strictEqual(fetchCalls.length, 1, "startMultiSimulation 중단 시 /api/reset만 호출되어야 합니다.");
  assert.strictEqual(fetchCalls[0].url, "/api/reset", "첫 호출은 /api/reset이어야 합니다.");
  const hasStartCall = fetchCalls.some(call => call.url === "/api/start");
  assert.strictEqual(hasStartCall, false, "reset 실패 시 /api/start가 절대 호출되어서는 안 됩니다.");

  // 3. fetch가 HTTP 200 성공을 반환할 때 정상 초기화 및 EventSource.close() 호출 검증
  mockFetchResponse = {
    ok: true,
    status: 200,
    json: async () => ({ status: "reset" })
  };
  const resetResultSuccess = await resetSimulationFn();
  assert.strictEqual(resetResultSuccess, true, "reset 성공 시 resetSimulation은 true를 반환해야 합니다.");
  assert.strictEqual(testEvtSource.closeCalled, true, "reset 성공 시에는 EventSource.close()가 호출되어야 합니다.");
  assert.strictEqual(getGlobal('evtSource'), null, "reset 성공 시 evtSource 변수가 null로 초기화되어야 합니다.");
  assert.strictEqual(getGlobal('historyByHouse').size, 0, "reset 성공 시 historyByHouse가 비워져야 합니다.");
  assert.strictEqual(getGlobal('activeConfiguredHouseholds').length, 0, "reset 성공 시 activeConfiguredHouseholds가 비워져야 합니다.");
  assert.strictEqual(getGlobal('chartMain').data.datasets[0].data.length, 0, "reset 성공 시 차트 데이터가 비워져야 합니다.");
  console.log("✔ Test 7 통과: resetSimulation 실패 시 SSE/상태 보존 및 성공 시 초기화 검증 완료");

  console.log("\n==========================================");
  console.log("🎉 모든 프런트엔드 실제 JavaScript 테스트 통과!");
  console.log("==========================================\n");
}

runTests().catch(err => {
  console.error("✘ 테스트 실행 중 오류 발생:", err);
  process.exit(1);
});
