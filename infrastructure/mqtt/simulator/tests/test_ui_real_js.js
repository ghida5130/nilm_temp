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
  let _id = id;
  const el = {
    get id() { return _id; },
    set id(val) {
      _id = val;
      if (val) domElements.set(val, this);
    },
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
    click() {},
    querySelector(sel) {
      const m = sel.match(/option\[value="([^"]+)"\]/);
      if (m) {
        const val = m[1];
        return this.children.find(c => c.tagName === 'OPTION' && (c.value === val || c.getAttribute('value') === val)) || null;
      }
      return null;
    }
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
  'speedSelect',
  'btnPause', 'badgeStatus', 'valPower', 'valApparent', 'valCurrent', 'valVoltage',
  'valPf', 'valReactive', 'valTime', 'valMaxPower', 'cardPower', 'timelineNotice',
  'eventTableBody', 'serverIndicator', 'chartMain', 'chartSecondary',
  'btnPresetPeak', 'btnPresetMissed', 'btnPresetFault', 'btnPresetAllRandom', 'btnPresetManual',
  'btnRunMulti', 'btnResetAll', 'btnManualToggle',
  'btnDemoNormalRoutine', 'btnDemoRoutineMissed', 'btnDemoSensorFault', 'faultDurationInput'
];
preRegisteredIds.forEach(id => getOrCreateElement(id));

// faultDurationInput 기본값 120 설정
const faultDurInputInit = getOrCreateElement('faultDurationInput', 'input');
faultDurInputInit.value = '120';

// speedSelect 옵션 사전 채우기
const speedSelectInit = getOrCreateElement('speedSelect', 'select');
['1000', '500', '200', '100'].forEach(val => {
  const opt = createMockElement('', 'option');
  opt.value = val;
  opt.setAttribute('value', val);
  speedSelectInit.appendChild(opt);
});

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
const setGlobal = (name, val) => vm.runInContext(`${name} = ${JSON.stringify(val)};`, context);

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

  // Test 8: startNormalRoutineDemo() 원클릭 정상 일상 시나리오 시작 검증
  console.log("\n[Test 8] startNormalRoutineDemo 원클릭 정상 일상 실행 검증");
  const startNormalRoutineDemo = getGlobal('startNormalRoutineDemo');
  assert.ok(typeof startNormalRoutineDemo === 'function', "startNormalRoutineDemo 함수가 존재해야 합니다.");

  fetchCalls.length = 0;
  mockFetchResponse = {
    ok: true,
    status: 200,
    json: async () => ({ status: "started" })
  };

  await startNormalRoutineDemo();

  // reset 호출 후 start 호출 확인
  assert.strictEqual(fetchCalls.length, 2, "reset과 start 총 2회 호출되어야 합니다.");
  assert.strictEqual(fetchCalls[0].url, "/api/reset", "첫 번째 호출은 /api/reset이어야 합니다.");
  assert.strictEqual(fetchCalls[1].url, "/api/start", "두 번째 호출은 /api/start이어야 합니다.");

  const startReqNormal = JSON.parse(fetchCalls[1].options.body);
  assert.strictEqual(startReqNormal.households.length, 1, "H001 가구 1개만 요청되어야 합니다.");
  assert.strictEqual(startReqNormal.households[0].house, "H001", "요청 가구가 H001이어야 합니다.");
  assert.strictEqual(startReqNormal.households[0].scenario, "normal_routine", "요청 시나리오가 normal_routine이어야 합니다.");
  console.log("✔ Test 8 통과: startNormalRoutineDemo가 초기화 후 H001/normal_routine을 정상 요청함");

  // Test 9: startRoutineMissedDemo() 원클릭 루틴 누락 시나리오 시작 검증
  console.log("\n[Test 9] startRoutineMissedDemo 원클릭 루틴 누락 실행 검증");
  const startRoutineMissedDemo = getGlobal('startRoutineMissedDemo');
  assert.ok(typeof startRoutineMissedDemo === 'function', "startRoutineMissedDemo 함수가 존재해야 합니다.");

  fetchCalls.length = 0;
  await startRoutineMissedDemo();

  assert.strictEqual(fetchCalls.length, 2, "reset과 start 총 2회 호출되어야 합니다.");
  assert.strictEqual(fetchCalls[0].url, "/api/reset", "첫 번째 호출은 /api/reset이어야 합니다.");
  assert.strictEqual(fetchCalls[1].url, "/api/start", "두 번째 호출은 /api/start이어야 합니다.");

  const startReqMissed = JSON.parse(fetchCalls[1].options.body);
  assert.strictEqual(startReqMissed.households.length, 1, "H001 가구 1개만 요청되어야 합니다.");
  assert.strictEqual(startReqMissed.households[0].house, "H001", "요청 가구가 H001이어야 합니다.");
  assert.strictEqual(startReqMissed.households[0].scenario, "routine_missed", "요청 시나리오가 routine_missed이어야 합니다.");
  console.log("✔ Test 9 통과: startRoutineMissedDemo가 초기화 후 H001/routine_missed를 정상 요청함");

  // Test 10: 원클릭 버튼에서 reset 실패 시(503 등) /api/start 중단 및 상태 보존 검증
  console.log("\n[Test 10] 원클릭 실행 시 초기화 실패(503) 방어 및 SSE/상태 보존 검증");
  const demoEvtSource = new MockEventSource("/api/stream");
  sandbox.testDemoEvtSource = demoEvtSource;
  vm.runInContext('evtSource = testDemoEvtSource', context);

  // 사전에 실행 중 상태 및 가구, 속도 상태 설정
  setGlobal('isSimulationRunning', true);
  setGlobal('activeConfiguredHouseholds', [{ house: "H001", scenario: "normal_routine" }]);
  setGlobal('intervalMs', 200);
  setGlobal('lastSuccessfulSpeedMs', 200);

  mockFetchResponse = {
    ok: false,
    status: 503,
    json: async () => ({ status: "error", message: "시뮬레이터 초기화 서비스 불가(503)" })
  };

  fetchCalls.length = 0;
  noticeErrorCalledWith = null;

  await startNormalRoutineDemo();

  assert.strictEqual(fetchCalls.length, 1, "reset 실패 시 /api/reset 1회만 호출되어야 합니다.");
  assert.strictEqual(fetchCalls[0].url, "/api/reset", "호출 엔드포인트는 /api/reset이어야 합니다.");
  assert.strictEqual(demoEvtSource.closeCalled, false, "reset 실패 시 기존 EventSource가 닫히지 않아야 합니다.");
  assert.ok(getGlobal('evtSource') !== null, "evtSource가 유지되어야 합니다.");
  assert.ok(noticeErrorCalledWith !== null, "사용자에게 에러 메시지가 표시되어야 합니다.");
  assert.strictEqual(getGlobal('isSimulationRunning'), true, "reset 실패 후 isSimulationRunning=true 가 유지되어야 합니다.");
  assert.strictEqual(getGlobal('activeConfiguredHouseholds').length, 1, "가구 설정이 유지되어야 합니다.");
  assert.strictEqual(getGlobal('intervalMs'), 200, "기존 배속 설정이 유지되어야 합니다.");

  // Reset 실패 후에도 isSimulationRunning이 true이므로 changeSpeed() 호출 시 /api/speed가 실제 호출되어야 함
  fetchCalls.length = 0;
  mockFetchResponse = {
    ok: true,
    status: 200,
    json: async () => ({ status: "speed_updated", interval: 0.1, speed: 10.0 })
  };
  const changeSpeedFunc = getGlobal('changeSpeed');
  await changeSpeedFunc("100");
  assert.strictEqual(fetchCalls.length, 1, "reset 실패 후 실행 유지 상태에서는 changeSpeed()가 /api/speed를 호출해야 합니다.");
  assert.strictEqual(fetchCalls[0].url, "/api/speed");

  fetchCalls.length = 0;
  mockFetchResponse = {
    ok: false,
    status: 503,
    json: async () => ({ status: "error", message: "시뮬레이터 초기화 서비스 불가(503)" })
  };
  await startRoutineMissedDemo();
  assert.strictEqual(fetchCalls.length, 1, "routine_missed 원클릭에서도 reset 실패 시 /api/start가 호출되지 않아야 합니다.");
  assert.strictEqual(fetchCalls[0].url, "/api/reset");
  assert.strictEqual(getGlobal('isSimulationRunning'), true, "두 번째 reset 실패 후에도 isSimulationRunning=true 유지");
  console.log("✔ Test 10 통과: 원클릭 시연 버튼의 reset 503 실패 방어 및 SSE 보존 확인");

  // Test 11: normal_routine SSE 이벤트 수신 시 UI 배지 및 전자레인지 칩 상태 검증
  console.log("\n[Test 11] normal_routine SSE 이벤트 수신 시 UI 배지, 가전 칩, 완료 상태 검증");
  getGlobal('observedHouse = "H001"');
  getGlobal('activeConfiguredHouseholds = [{ house: "H001", scenario: "normal_routine" }]');

  // 1) cycle 243: 전자레인지 가동 중
  const sseNormalRunning = {
    house: "H001",
    scenario: "normal_routine",
    sec: 243,
    status: "running",
    totalP: 995.0,
    totalQ: 180.0,
    apparentS: 1011.1,
    currentA: 4.59,
    pf: 0.984,
    voltage: 220.0,
    simTimeKst: "08:09:00",
    activeNames: ["microwave"],
    devices: {
      microwave: { enabled: true, state: "RUNNING", manualHold: false }
    },
    eventNoticeText: "08:09 아침 정상 루틴 시작 — 전자레인지 가동"
  };
  processServerMetrics(sseNormalRunning);

  const badgeEl = getOrCreateElement('badgeStatus');
  const chipMw = getOrCreateElement('chip_microwave');
  const stateMw = getOrCreateElement('state_microwave');

  assert.ok(badgeEl.textContent.includes("정상 루틴"), "전자레인지 가동 중 정상 루틴 배지 텍스트가 표시되어야 합니다.");
  assert.ok(chipMw.className.includes("active"), "전자레인지 가동 시 chip_microwave에 active 클래스가 적용되어야 합니다.");
  assert.strictEqual(stateMw.textContent, "ON", "전자레인지 상태 라벨이 ON이어야 합니다.");

  // 2) cycle 303: 전자레인지 종료
  const sseNormalOff = {
    house: "H001",
    scenario: "normal_routine",
    sec: 303,
    status: "running",
    totalP: 55.2,
    totalQ: 23.0,
    apparentS: 59.8,
    currentA: 0.27,
    pf: 0.923,
    voltage: 220.0,
    simTimeKst: "08:10:00",
    activeNames: [],
    devices: {
      microwave: { enabled: false, state: "OFF", manualHold: false }
    },
    eventNoticeText: "08:10 이전 전자레인지 60초 사용 완료 — 대기전력 복귀"
  };
  processServerMetrics(sseNormalOff);

  assert.ok(!chipMw.className.includes("active"), "전자레인지 종료 시 active 클래스가 해제되어야 합니다.");
  assert.strictEqual(stateMw.textContent, "OFF", "전자레인지 상태 라벨이 OFF이어야 합니다.");
  assert.ok(badgeEl.textContent.includes("정상 일상"), "대기전력 복귀 시 정상 일상 루틴 진행 중 텍스트가 표시되어야 합니다.");

  // 3) cycle 308: 시연 완료
  const sseNormalComplete = {
    house: "H001",
    scenario: "normal_routine",
    sec: 308,
    status: "completed",
    totalP: 55.0,
    totalQ: 23.0,
    apparentS: 59.6,
    currentA: 0.27,
    pf: 0.923,
    voltage: 220.0,
    simTimeKst: "08:10:05",
    activeNames: [],
    devices: {
      microwave: { enabled: false, state: "OFF", manualHold: false }
    },
    eventNoticeText: "H001 정상 일상 전력 패턴 발행 완료"
  };
  processServerMetrics(sseNormalComplete);

  assert.strictEqual(badgeEl.textContent, "H001 정상 일상 전력 패턴 발행 완료", "cycle 308 완료 시 정상 완료 텍스트가 표시되어야 합니다.");
  console.log("✔ Test 11 통과: normal_routine SSE 배지, 전자레인지 칩 동작, 완료 상태 정상 렌더링 확인");

  // Test 12: applyPreset('normal_single') 프리셋 동작 검증
  console.log("\n[Test 12] applyPreset normal_single 및 missed_single 검증");
  const applyPreset = getGlobal('applyPreset');
  assert.ok(typeof applyPreset === 'function', "applyPreset 함수가 존재해야 합니다.");

  applyPreset('normal_single');
  assert.strictEqual(getOrCreateElement('chk_H001', 'input').checked, true, "H001은 체크되어야 합니다.");
  assert.strictEqual(getOrCreateElement('scenario_H001', 'select').value, "normal_routine", "H001 시나리오는 normal_routine이어야 합니다.");
  for (let i = 2; i <= 10; i++) {
    const h = `H${String(i).padStart(3, '0')}`;
    assert.strictEqual(getOrCreateElement(`chk_${h}`, 'input').checked, false, `${h}는 체크 해제되어야 합니다.`);
  }

  applyPreset('missed_single');
  assert.strictEqual(getOrCreateElement('chk_H001', 'input').checked, true, "H001은 체크되어야 합니다.");
  assert.strictEqual(getOrCreateElement('scenario_H001', 'select').value, "routine_missed", "H001 시나리오는 routine_missed이어야 합니다.");
  for (let i = 2; i <= 10; i++) {
    const h = `H${String(i).padStart(3, '0')}`;
    assert.strictEqual(getOrCreateElement(`chk_${h}`, 'input').checked, false, `${h}는 체크 해제되어야 합니다.`);
  }
  console.log("✔ Test 12 통과: applyPreset normal_single 및 missed_single 동작 확인");

  // Test 13: H001 선택 목록에는 normal_routine 존재, H002~H010에는 normal_routine 선택 불가 검증
  console.log("\n[Test 13] H001 선택 목록에는 normal_routine 존재 및 H002~H010 제외 검증");
  const selH001 = getOrCreateElement('scenario_H001', 'select');
  const h001Options = (selH001.options || []).map(opt => opt.value);
  assert.ok(h001Options.includes('normal_routine'), "H001 시나리오 목록에는 normal_routine이 반드시 포함되어야 합니다.");

  for (let i = 2; i <= 10; i++) {
    const h = `H${String(i).padStart(3, '0')}`;
    const selH = getOrCreateElement(`scenario_${h}`, 'select');
    const hOptions = (selH.options || []).map(opt => opt.value);
    assert.ok(!hOptions.includes('normal_routine'), `${h} 시나리오 목록에는 normal_routine이 포함되지 않아야 합니다.`);
  }
  console.log("✔ Test 13 통과: H001만 normal_routine 선택 가능 및 H002~H010 제외 확인");

  // Test 14: 초기 화면 및 resetSimulation 완료 후 timelineNotice 안내 문구 검증
  console.log("\n[Test 14] 초기 화면 및 resetSimulation 완료 후 timelineNotice 안내 문구 검증");
  assert.ok(
    htmlContent.includes("[준비 완료] H001 정상 일상 또는 H001 이상 감지 버튼을 선택하세요."),
    "초기 HTML의 timelineNotice 배너에 발표용 두 버튼 안내 문구가 포함되어야 합니다."
  );

  // resetSimulation 성공 시 안내 문구 확인
  const resetSimulation = getGlobal('resetSimulation');
  assert.ok(typeof resetSimulation === 'function', "resetSimulation 함수가 존재해야 합니다.");
  mockFetchResponse = {
    ok: true,
    status: 200,
    json: async () => ({ status: 'reset' })
  };
  await resetSimulation();
  const noticeEl = getOrCreateElement('timelineNotice');
  assert.strictEqual(
    noticeEl.innerHTML,
    "[준비 완료] H001 정상 일상 또는 H001 이상 감지 버튼을 선택하세요.",
    "resetSimulation 성공 후 timelineNotice가 발표용 두 버튼 안내 문구로 갱신되어야 합니다."
  );
  console.log("✔ Test 14 통과: 초기 및 reset 완료 후 발표용 안내 문구 정합성 확인");

  // Test 15: 브라우저 내부 normal_routine 표시 시각 계산식 오프바이원 수정 검증
  console.log("\n[Test 15] 브라우저 내부 normal_routine tick() 가상 시각 계산식 오프바이원 수정 검증");
  assert.ok(
    htmlContent.includes("8 * 3600 + 4 * 60 + 58 + (currentSec - 1)"),
    "waveform_viewer.html의 normal_routine 가상 시각 계산식이 (currentSec - 1)로 수정되어 있어야 합니다."
  );

  function calcVirtualKstTime(sec) {
    const totalSecKst = 8 * 3600 + 4 * 60 + 58 + (sec - 1);
    const curH = Math.floor(totalSecKst / 3600) % 24;
    const curM = Math.floor((totalSecKst % 3600) / 60);
    const curS = totalSecKst % 60;
    return `${String(curH).padStart(2, '0')}:${String(curM).padStart(2, '0')}:${String(curS).padStart(2, '0')}`;
  }

  assert.strictEqual(calcVirtualKstTime(1), "08:04:58", "cycle 1 가상 시각은 08:04:58이어야 합니다.");
  assert.strictEqual(calcVirtualKstTime(242), "08:08:59", "cycle 242 가상 시각은 08:08:59이어야 합니다.");
  assert.strictEqual(calcVirtualKstTime(243), "08:09:00", "cycle 243 가상 시각은 08:09:00이어야 합니다.");
  assert.strictEqual(calcVirtualKstTime(302), "08:09:59", "cycle 302 가상 시각은 08:09:59이어야 합니다.");
  assert.strictEqual(calcVirtualKstTime(303), "08:10:00", "cycle 303 가상 시각은 08:10:00이어야 합니다.");
  assert.strictEqual(calcVirtualKstTime(308), "08:10:05", "cycle 308 가상 시각은 08:10:05이어야 합니다.");
  console.log("✔ Test 15 통과: 브라우저 내부 normal_routine 가상 시각 계산식 오프바이원 수정 및 시간표 일치 확인");

  // Test 16: 10x 배속 옵션 존재 및 기존 옵션 유지 검증
  {
    console.log("\n[Test 16] 10x 배속 옵션 존재 및 기존 옵션 유지 검증");
    assert.ok(
      htmlContent.includes('<option value="100">10x 배속 (0.1초)</option>'),
      "#speedSelect에 10x 배속(0.1초) 옵션이 반드시 존재해야 합니다."
    );
    assert.ok(htmlContent.includes('value="1000"'), "1000ms 옵션 유지");
    assert.ok(htmlContent.includes('value="500"'), "500ms 옵션 유지");
    assert.ok(htmlContent.includes('value="200"'), "200ms 옵션 유지");
    console.log("✔ Test 16 통과: 10x 배속 옵션 및 기존 옵션 완벽 유지 확인");
  }

  // Test 17: startMultiSimulation()이 선택한 interval을 /api/start에 전달 검증
  {
    console.log("\n[Test 17] startMultiSimulation() 선택 interval 전달 검증");
    const speedSelect = getOrCreateElement('speedSelect', 'select');
    speedSelect.value = "100"; // 10x (0.1초)
    fetchCalls.length = 0;
    mockFetchResponse = {
      ok: true,
      status: 200,
      json: async () => ({
        status: "started",
        households: [{ house: "H001", scenario: "peak" }],
        interval: 0.1,
        speed: 10.0
      })
    };

    const startMultiSimulation = getGlobal('startMultiSimulation');
    assert.ok(typeof startMultiSimulation === 'function', "startMultiSimulation 함수 존재");
    await startMultiSimulation();

    const startCall = fetchCalls.find(c => c.url === '/api/start');
    assert.ok(startCall, "/api/start 요청이 발생해야 합니다.");
    const startPayload = JSON.parse(startCall.options.body);
    assert.strictEqual(startPayload.interval, 0.1, "/api/start에 interval: 0.1이 전달되어야 합니다.");
    assert.strictEqual(getGlobal('isSimulationRunning'), true, "시작 성공 후 isSimulationRunning=true 여야 합니다.");
    console.log("✔ Test 17 통과: startMultiSimulation()이 선택한 interval(0.1s)을 /api/start에 정상 전달");
  }

  // Test 18: 원클릭 정상/이상 버튼에서도 선택 배속 전달 검증
  {
    console.log("\n[Test 18] 원클릭 정상/이상 버튼에서도 선택 배속 전달 검증");
    const speedSelect = getOrCreateElement('speedSelect', 'select');
    speedSelect.value = "200"; // 5x (0.2초)
    fetchCalls.length = 0;
    mockFetchResponse = {
      ok: true,
      status: 200,
      json: async () => ({ status: "started" })
    };

    const startNormalRoutineDemo = getGlobal('startNormalRoutineDemo');
    await startNormalRoutineDemo();
    const normalCall = fetchCalls.find(c => c.url === '/api/start');
    assert.ok(normalCall, "normal_routine 시작 호출 발생");
    const normalPayload = JSON.parse(normalCall.options.body);
    assert.strictEqual(normalPayload.interval, 0.2, "정상 루틴 실행 시 interval: 0.2가 전달되어야 합니다.");

    speedSelect.value = "500"; // 2x (0.5초)
    fetchCalls.length = 0;
    const startRoutineMissedDemo = getGlobal('startRoutineMissedDemo');
    await startRoutineMissedDemo();
    const missedCall = fetchCalls.find(c => c.url === '/api/start');
    assert.ok(missedCall, "routine_missed 시작 호출 발생");
    const missedPayload = JSON.parse(missedCall.options.body);
    assert.strictEqual(missedPayload.interval, 0.5, "이상 감지 실행 시 interval: 0.5가 전달되어야 합니다.");
    console.log("✔ Test 18 통과: 원클릭 정상/이상 버튼에서 선택 배속 전달 확인");
  }

  // Test 19: 실행 중 changeSpeed()가 /api/speed 호출 검증
  {
    console.log("\n[Test 19] 실행 중 changeSpeed()가 /api/speed 호출 검증");
    const changeSpeed = getGlobal('changeSpeed');
    assert.ok(typeof changeSpeed === 'function', "changeSpeed 함수 존재");
    fetchCalls.length = 0;
    mockFetchResponse = {
      ok: true,
      status: 200,
      json: async () => ({ status: "speed_updated", interval: 0.2, speed: 5.0 })
    };

    // 실행 중 상태
    setGlobal('isSimulationRunning', true);
    await changeSpeed("200");

    const speedCall = fetchCalls.find(c => c.url === '/api/speed');
    assert.ok(speedCall, "실행 중 /api/speed 요청이 전송되어야 합니다.");
    const speedPayload = JSON.parse(speedCall.options.body);
    assert.strictEqual(speedPayload.interval, 0.2, "interval: 0.2가 /api/speed로 전송되어야 합니다.");
    assert.strictEqual(getGlobal('intervalMs'), 200, "성공 시 intervalMs가 200으로 변경되어야 합니다.");
    console.log("✔ Test 19 통과: 실행 중 changeSpeed()가 /api/speed 정상 호출 및 intervalMs 반영");
  }

  // Test 20: 시작 전 변경은 API를 호출하지 않음 검증
  {
    console.log("\n[Test 20] 시작 전 배속 변경은 API를 호출하지 않음 검증");
    const changeSpeed = getGlobal('changeSpeed');
    setGlobal('isSimulationRunning', false);
    fetchCalls.length = 0;
    await changeSpeed("500");
    const noSpeedCall = fetchCalls.find(c => c.url === '/api/speed');
    assert.strictEqual(noSpeedCall, undefined, "시뮬레이션 시작 전에는 /api/speed를 호출하지 않아야 합니다.");
    assert.strictEqual(getGlobal('intervalMs'), 500, "시작 전 변경값은 로컬 intervalMs에 정상 저장되어야 합니다.");
    console.log("✔ Test 20 통과: 시작 전 배속 변경 시 API 미호출 확인");
  }

  // Test 21: Pause 중 변경도 API 호출 검증
  {
    console.log("\n[Test 21] Pause 중 배속 변경도 API 호출 검증");
    const changeSpeed = getGlobal('changeSpeed');
    setGlobal('isSimulationRunning', true);
    setGlobal('isPaused', true);
    fetchCalls.length = 0;
    mockFetchResponse = {
      ok: true,
      status: 200,
      json: async () => ({ status: "speed_updated", interval: 0.1, speed: 10.0 })
    };
    await changeSpeed("100");
    const pausedSpeedCall = fetchCalls.find(c => c.url === '/api/speed');
    assert.ok(pausedSpeedCall, "Pause 상태에서도 /api/speed가 호출되어야 합니다.");
    assert.strictEqual(getGlobal('intervalMs'), 100, "Pause 상태에서도 intervalMs가 100으로 변경되어야 합니다.");
    console.log("✔ Test 21 통과: Pause 중 배속 변경 시 API 호출 및 새 배속 적용 확인");
  }

  // Test 22: API 실패 시 선택값 및 intervalMs 복원 검증
  {
    console.log("\n[Test 22] API 실패 시 선택값 및 intervalMs 복원 검증");
    const changeSpeed = getGlobal('changeSpeed');
    const speedSelect = getOrCreateElement('speedSelect', 'select');
    setGlobal('isSimulationRunning', true);
    setGlobal('isPaused', false);
    setGlobal('intervalMs', 100);
    setGlobal('lastSuccessfulSpeedMs', 100);
    speedSelect.value = "100";

    mockFetchResponse = {
      ok: false,
      status: 500,
      json: async () => ({ message: "서버 내부 오류" })
    };
    fetchCalls.length = 0;

    await changeSpeed("500");
    assert.strictEqual(speedSelect.value, "100", "API 실패 시 speedSelect가 마지막 성공값('100')으로 롤백되어야 합니다.");
    assert.strictEqual(getGlobal('intervalMs'), 100, "API 실패 시 intervalMs도 마지막 성공값(100)으로 롤백되어야 합니다.");
    console.log("✔ Test 22 통과: API 실패 시 선택값 및 intervalMs 자동 복원 확인");
  }

  // Test 23: 로컬 timerId 모드 동작 유지 검증
  {
    console.log("\n[Test 23] 로컬 timerId 모드 동작 유지 검증");
    const changeSpeed = getGlobal('changeSpeed');
    setGlobal('isServerConnected', false);
    setGlobal('isSimulationRunning', false);
    fetchCalls.length = 0;
    await changeSpeed("200");
    assert.strictEqual(getGlobal('intervalMs'), 200, "로컬 모드에서 intervalMs가 200으로 변경되어야 합니다.");
    assert.strictEqual(fetchCalls.length, 0, "로컬 모드에서는 API 호출이 없어야 합니다.");
    setGlobal('isServerConnected', true); // 복원
    console.log("✔ Test 23 통과: 로컬 timerId 모드 동작 유지 확인");
  }

  // Test 24: 완료/Reset/시작 실패 후 isSimulationRunning=false 검증
  {
    console.log("\n[Test 24] 완료/Reset/시작 실패 후 isSimulationRunning=false 검증");
    // 1. Reset 시
    setGlobal('isSimulationRunning', true);
    mockFetchResponse = {
      ok: true,
      status: 200,
      json: async () => ({ status: "reset" })
    };
    const resetSimulationFunc = getGlobal('resetSimulation');
    await resetSimulationFunc();
    assert.strictEqual(getGlobal('isSimulationRunning'), false, "reset 후 isSimulationRunning=false");

    // 2. 시작 실패 시
    mockFetchResponse = {
      ok: false,
      status: 400,
      json: async () => ({ message: "invalid payload" })
    };
    const startMultiSimulation = getGlobal('startMultiSimulation');
    await startMultiSimulation();
    assert.strictEqual(getGlobal('isSimulationRunning'), false, "시작 실패 후 isSimulationRunning=false");

    // 3. 자연 완료 시
    setGlobal('activeConfiguredHouseholds', [{ house: "H001", scenario: "peak" }]);
    setGlobal('isSimulationRunning', true);
    const processServerMetrics = getGlobal('processServerMetrics');
    const completeEvent = {
      house: "H001",
      scenario: "peak",
      status: "completed",
      sec: 60,
      totalP: 55.0,
      apparentS: 55.0,
      totalQ: 0.0,
      currentA: 0.25,
      pf: 1.0,
      voltage: 220.0
    };
    processServerMetrics(completeEvent);
    assert.strictEqual(getGlobal('isSimulationRunning'), false, "모든 가구 완료 후 isSimulationRunning=false");
    console.log("✔ Test 24 통과: 완료/Reset/시작 실패 후 isSimulationRunning=false 상태 전이 확인");
  }

  // Test 25: 빠른 연속 변경에 대한 요청 직렬화 또는 드롭다운 잠금 검증
  {
    console.log("\n[Test 25] 빠른 연속 변경에 대한 요청 직렬화 및 잠금 검증");
    const changeSpeed = getGlobal('changeSpeed');
    const speedSelect = getOrCreateElement('speedSelect', 'select');
    setGlobal('isSimulationRunning', true);
    setGlobal('isPaused', false);
    setGlobal('lastSuccessfulSpeedMs', 1000);
    speedSelect.value = "1000";

    let resolveSpeedPromise;
    const delayedPromise = new Promise(resolve => {
      resolveSpeedPromise = resolve;
    });

    const origFetch = sandbox.fetch;
    sandbox.fetch = async (url, options) => {
      if (url === '/api/speed') {
        await delayedPromise;
        return {
          ok: true,
          status: 200,
          json: async () => ({ status: "speed_updated", interval: 0.5, speed: 2.0 })
        };
      }
      return origFetch(url, options);
    };

    // 1차 요청 발송 (in-flight)
    const req1 = changeSpeed("500");
    assert.strictEqual(speedSelect.disabled, true, "요청 처리 중 드롭다운이 disabled 잠금되어야 합니다.");
    assert.strictEqual(getGlobal('isSpeedUpdating'), true, "isSpeedUpdating=true 로 직렬화되어야 합니다.");

    // 2차 빠른 연속 요청 시도 -> 무시되어야 함
    await changeSpeed("200");

    // 1차 요청 완료
    resolveSpeedPromise();
    await req1;

    assert.strictEqual(speedSelect.disabled, false, "요청 완료 후 드롭다운이 다시 활성화되어야 합니다.");
    assert.strictEqual(getGlobal('isSpeedUpdating'), false, "isSpeedUpdating=false 로 복원되어야 합니다.");
    sandbox.fetch = origFetch; // 원복
    console.log("✔ Test 25 통과: 빠른 연속 변경 시 직렬화 및 잠금 제어 완벽 확인");
  }

  // Test 26: Stop 실패 상태 보존 검증
  {
    console.log("\n[Test 26] Stop 실패 상태 보존 검증");
    const stopSimulationFunc = getGlobal('stopSimulation');
    const stopEvtSource = new MockEventSource("/api/stream");
    sandbox.testStopEvtSource = stopEvtSource;
    vm.runInContext('evtSource = testStopEvtSource', context);

    // 1. /api/stop 503 실패 시
    setGlobal('isSimulationRunning', true);
    mockFetchResponse = {
      ok: false,
      status: 503,
      json: async () => ({ message: "Stop failed 503" })
    };
    const res1 = await stopSimulationFunc();
    assert.strictEqual(res1, false, "Stop 실패 시 false 반환");
    assert.strictEqual(getGlobal('isSimulationRunning'), true, "Stop 503 실패 시 isSimulationRunning=true 유지");
    assert.strictEqual(stopEvtSource.closeCalled, false, "Stop 실패 시 EventSource 유지");

    // 2. 네트워크 에러 발생 시
    const origFetch = sandbox.fetch;
    sandbox.fetch = async () => { throw new Error("Network error during stop"); };
    const res2 = await stopSimulationFunc();
    assert.strictEqual(res2, false, "네트워크 에러 시 false 반환");
    assert.strictEqual(getGlobal('isSimulationRunning'), true, "네트워크 에러 시 isSimulationRunning=true 유지");
    assert.strictEqual(stopEvtSource.closeCalled, false, "네트워크 에러 시 EventSource 유지");
    sandbox.fetch = origFetch;

    // 3. Stop 성공 (HTTP 200)
    mockFetchResponse = {
      ok: true,
      status: 200,
      json: async () => ({ status: "stopped" })
    };
    const res3 = await stopSimulationFunc();
    assert.strictEqual(res3, true, "Stop 성공 시 true 반환");
    assert.strictEqual(getGlobal('isSimulationRunning'), false, "Stop 성공 시 isSimulationRunning=false 전이");
    assert.strictEqual(stopEvtSource.closeCalled, true, "Stop 성공 시 EventSource 닫힘");
    console.log("✔ Test 26 통과: Stop 실패 상태 보존 및 성공 시 정상 종료 처리 확인");
  }

  // Test 27: 페이지 재접속 상태 동기화 검증
  {
    console.log("\n[Test 27] 페이지 재접속 상태 동기화 검증");
    const checkServerConnection = getGlobal('checkServerConnection');
    const speedSelect = getOrCreateElement('speedSelect', 'select');

    // 초기 상태 초기화
    setGlobal('isSimulationRunning', false);
    setGlobal('isPaused', false);
    setGlobal('intervalMs', 1000);
    setGlobal('lastSuccessfulSpeedMs', 1000);
    speedSelect.value = "1000";

    // /api/status 가 실행 중인 상태 반환
    mockFetchResponse = {
      ok: true,
      status: 200,
      json: async () => ({
        is_running: true,
        is_paused: false,
        interval: 0.1,
        speed: 10.0,
        active_households: {
          "H001": {
            scenario: "normal_routine",
            cycle_count: 100,
            status: "running"
          }
        },
        broker: "localhost:1883"
      })
    };

    fetchCalls.length = 0;
    await checkServerConnection();

    assert.strictEqual(getGlobal('isSimulationRunning'), true, "서버가 is_running=true이면 UI도 isSimulationRunning=true");
    assert.strictEqual(getGlobal('intervalMs'), 100, "intervalMs가 100ms로 동기화되어야 합니다.");
    assert.strictEqual(getGlobal('lastSuccessfulSpeedMs'), 100, "lastSuccessfulSpeedMs가 100ms로 동기화되어야 합니다.");
    assert.strictEqual(speedSelect.value, "100", "speedSelect 드롭다운 값이 100(10x)으로 갱신되어야 합니다.");

    const activeHh = getGlobal('activeConfiguredHouseholds');
    assert.strictEqual(activeHh.length, 1, "activeConfiguredHouseholds에 H001이 복원되어야 합니다.");
    assert.strictEqual(activeHh[0].house, "H001");
    assert.strictEqual(activeHh[0].scenario, "normal_routine");
    assert.ok(getGlobal('evtSource') !== null, "실행 중 동기화 시 SSE 스트림이 다시 연결되어야 합니다.");

    // 동기화 후 changeSpeed("200") 호출 시 /api/speed 가 실제로 호출되는지 검증
    fetchCalls.length = 0;
    mockFetchResponse = {
      ok: true,
      status: 200,
      json: async () => ({ status: "speed_updated", interval: 0.2, speed: 5.0 })
    };
    const changeSpeedFunc = getGlobal('changeSpeed');
    await changeSpeedFunc("200");

    assert.strictEqual(fetchCalls.length, 1, "동기화 후 changeSpeed 호출 시 /api/speed가 호출되어야 합니다.");
    assert.strictEqual(fetchCalls[0].url, "/api/speed");
    const sentBody = JSON.parse(fetchCalls[0].options.body);
    assert.strictEqual(sentBody.interval, 0.2, "새 interval(0.2s)이 전달되어야 합니다.");
    assert.strictEqual(getGlobal('intervalMs'), 200, "intervalMs가 200ms로 갱신되어야 합니다.");
    console.log("✔ Test 27 통과: /api/status 상태 동기화 및 SSE 재연결, 배속 제어 정상 연동 확인");
  }

  // Test 28: 부분 완료 다중 가구 상태 재접속 및 잔여 가구 완료 시 전체 종료 처리 검증
  {
    console.log("\n[Test 28] 부분 완료 다중 가구 상태 재접속 및 전체 종료 처리 검증");
    const checkServerConnection = getGlobal('checkServerConnection');
    const processServerMetrics = getGlobal('processServerMetrics');
    const btnPause = getOrCreateElement('btnPause');

    // 1. 초기 상태 설정
    setGlobal('isSimulationRunning', false);
    setGlobal('isPaused', false);
    setGlobal('observedHouse', 'H001');

    mockFetchResponse = {
      ok: true,
      status: 200,
      json: async () => ({
        is_running: true,
        is_paused: false,
        interval: 0.1,
        active_households: {
          "H001": {
            scenario: "normal_routine",
            cycle_count: 100,
            status: "running"
          },
          "H002": {
            scenario: "peak",
            cycle_count: 60,
            status: "completed"
          }
        },
        last_metrics_by_house: {
          "H001": {
            house: "H001",
            scenario: "normal_routine",
            status: "running",
            sec: 100,
            now_iso: "2026-09-15T00:01:39.000Z",
            totalP: 75.0,
            apparentS: 85.0,
            currentA: 0.38,
            voltage: 220.0,
            pf: 0.88,
            totalQ: 40.0
          },
          "H002": {
            house: "H002",
            scenario: "peak",
            status: "completed",
            sec: 60,
            now_iso: "2026-09-15T00:00:59.000Z",
            totalP: 60.0,
            apparentS: 65.0,
            currentA: 0.28,
            voltage: 220.0,
            pf: 0.92,
            totalQ: 25.0
          }
        }
      })
    };

    fetchCalls.length = 0;
    await checkServerConnection();

    // 검증 1: checkServerConnection() 후 latestMetricsByHouse에 두 가구가 모두 복원됨
    const metricsMap = getGlobal('latestMetricsByHouse');
    assert.strictEqual(metricsMap.size, 2, "latestMetricsByHouse에 두 가구가 모두 복원되어야 합니다.");
    assert.ok(metricsMap.has('H001'), "H001 메트릭이 복원되어야 합니다.");
    assert.ok(metricsMap.has('H002'), "H002 메트릭이 복원되어야 합니다.");

    // 검증 2: 이미 완료된 H002의 status === 'completed'가 유지됨
    const h2Metrics = metricsMap.get('H002');
    assert.strictEqual(h2Metrics.status, 'completed', "H002의 status는 'completed'로 유지되어야 합니다.");
    assert.strictEqual(h2Metrics.sec, 60, "H002의 sec가 60으로 복원되어야 합니다.");

    const h1Metrics = metricsMap.get('H001');
    assert.strictEqual(h1Metrics.status, 'running', "H001의 status는 'running'이어야 합니다.");
    assert.strictEqual(getGlobal('isSimulationRunning'), true, "서버가 실행 중이므로 isSimulationRunning=true여야 합니다.");
    assert.strictEqual(btnPause.disabled, false, "실행 중 재접속 시 Pause 버튼은 활성화되어야 합니다.");

    const activeEvtSource = getGlobal('evtSource');
    assert.ok(activeEvtSource !== null, "실행 중 재접속 시 SSE 스트림이 연결되어야 합니다.");

    // 검증 3: 실행 중이던 H001의 completed SSE 이벤트를 processServerMetrics()로 전달
    const h1CompletedEvent = {
      house: "H001",
      scenario: "normal_routine",
      status: "completed",
      sec: 308,
      cycle_count: 308,
      now_iso: "2026-09-15T00:05:07.000Z",
      simTimeKst: "08:10:05",
      totalP: 75.0,
      apparentS: 85.0,
      currentA: 0.38,
      voltage: 220.0,
      pf: 0.88,
      totalQ: 40.0
    };
    processServerMetrics(h1CompletedEvent);

    // 검증 4 & 5: checkAllHouseholdsFinished()가 전체 완료를 인식하여 isSimulationRunning === false
    assert.strictEqual(getGlobal('isSimulationRunning'), false, "모든 가구 완료 시 isSimulationRunning=false 전이");

    // 검증 6: EventSource가 정상 종료됨
    assert.strictEqual(activeEvtSource.closeCalled, true, "모든 가구 완료 시 EventSource.close() 호출 보장");
    assert.strictEqual(getGlobal('evtSource'), null, "evtSource 참조가 null로 정리되어야 합니다.");

    // 검증 7: Pause 버튼이 비활성화됨
    assert.strictEqual(btnPause.disabled, true, "전체 가구 완료 후 Pause 버튼은 비활성화되어야 합니다.");

    console.log("✔ Test 28 통과: 부분 완료 상태 재접속 후 복원 및 잔여 가구 완료 시 전체 정상 종료 확인");
  }

  // -------------------------------------------------------------
  // Test 29: sensor_fault 프리셋, 원클릭 시연, SSE null 안전 렌더링, 차트 gap 및 CSV 건너뛰기 검증
  // -------------------------------------------------------------
  {
    console.log("\n[Test 29] sensor_fault UI 연동, null-safe 렌더링, 차트 null-gap 및 CSV 제외 검증");
    const applyPreset = getGlobal('applyPreset');
    const startSensorFaultDemo = getGlobal('startSensorFaultDemo');
    const processServerMetrics = getGlobal('processServerMetrics');
    const getCsvContentForHouse = getGlobal('getCsvContentForHouse');
    const chartMain = getGlobal('chartMain');

    // 1. applyPreset('fault_single') 검증
    applyPreset('fault_single');
    const chkH1 = getOrCreateElement('chk_H001');
    const selH1 = getOrCreateElement('scenario_H001');
    const chkH2 = getOrCreateElement('chk_H002');
    assert.strictEqual(chkH1.checked, true, "H001은 체크되어야 합니다.");
    assert.strictEqual(selH1.value, 'sensor_fault', "H001 시나리오는 sensor_fault여야 합니다.");
    assert.strictEqual(chkH2.checked, false, "H002는 체크 해제되어야 합니다.");

    // 2. startSensorFaultDemo() 호출 검증
    fetchCalls.length = 0;
    mockFetchResponse = {
      ok: true,
      status: 200,
      json: async () => ({ status: "started" })
    };

    await startSensorFaultDemo();
    const startCall = fetchCalls.find(c => c.url === '/api/start');
    assert.ok(startCall, "/api/start가 호출되어야 합니다.");
    const startReqFault = JSON.parse(startCall.options.body);
    assert.strictEqual(startReqFault.households[0].scenario, 'sensor_fault', "start payload scenario는 sensor_fault여야 합니다.");

    // 3. cycle 1~10 정상 계측치 SSE 수신 (cycle 10)
    const normalSse = {
      house: "H001",
      scenario: "sensor_fault",
      status: "running",
      sec: 10,
      cycle_count: 10,
      now_iso: "2026-09-17T00:00:10.000Z",
      simTimeKst: "09:00:10",
      measurementAvailable: true,
      sensorFault: false,
      gapElapsedSec: 0,
      gapRemainingSec: 120,
      totalP: 55.4,
      apparentS: 60.1,
      currentA: 0.27,
      voltage: 220.0,
      pf: 0.92,
      totalQ: 23.1,
      activeNames: [],
      eventNoticeText: null
    };
    processServerMetrics(normalSse);

    const valPower = getOrCreateElement('valPower');
    assert.strictEqual(valPower.textContent, "55.4", "정상 계측 시 소비전력이 표시되어야 합니다.");

    // 4. cycle 11 센서 고장 시작 SSE 수신 (결측 구간: totalP 등 null)
    const faultSse = {
      house: "H001",
      scenario: "sensor_fault",
      status: "running",
      sec: 11,
      cycle_count: 11,
      now_iso: "2026-09-17T00:00:11.000Z",
      simTimeKst: "09:00:11",
      measurementAvailable: false,
      sensorFault: true,
      gapElapsedSec: 1,
      gapRemainingSec: 119,
      totalP: null,
      apparentS: null,
      currentA: null,
      voltage: null,
      pf: null,
      totalQ: null,
      activeNames: [],
      eventNoticeText: "센서 고장 시작 — 전력 계측 MQTT 메시지 발행 중단 (120초간)"
    };

    // null 값에도 toFixed TypeError 없이 정상 렌더링되는지 확인
    assert.doesNotThrow(() => {
      processServerMetrics(faultSse);
    }, "null 상태의 전력값 처리 시 toFixed 오류가 발생하지 않아야 합니다.");

    // 5. 결측 구간 UI 렌더링 확인 (— 표시, 센서 고장 배지)
    assert.strictEqual(valPower.textContent, "—", "고장 중 valPower는 '—'로 표시되어야 합니다.");
    const valCurrent = getOrCreateElement('valCurrent');
    assert.strictEqual(valCurrent.textContent, "—", "고장 중 전류는 '—'로 표시되어야 합니다.");
    const powerCellH1 = getOrCreateElement('power_val_H001');
    assert.strictEqual(powerCellH1.textContent, "—", "가구 테이블의 전력값도 '—'로 표시되어야 합니다.");

    const badgeStatus = getOrCreateElement('badgeStatus');
    assert.ok(badgeStatus.textContent.includes("센서 고장"), "배지에 '센서 고장'이 표시되어야 합니다.");

    // 6. 차트 및 히스토리 버퍼에 null이 푸시되어 끊긴 선(null gap)을 형성하는지 검증
    const hist = getGlobal('getOrCreateHistory')('H001');
    const lastHistP = hist.activeP[hist.activeP.length - 1];
    assert.strictEqual(lastHistP, null, "고장 구간 히스토리에는 null이 저장되어야 합니다.");

    const lastChartP = chartMain.data.datasets[0].data[chartMain.data.datasets[0].data.length - 1];
    assert.strictEqual(lastChartP, null, "차트 데이터셋에도 null이 전달되어 시각적으로 끊겨 보여야 합니다.");

    // 7. CSV 내보내기 시 결측(null) 행은 포함되지 않아야 함
    const csvContent = getCsvContentForHouse('H001');
    assert.ok(csvContent, "CSV 내용이 생성되어야 합니다.");
    const csvLines = csvContent.trim().split("\r\n");
    // 헤더 + cycle 10 (1행) = 총 2행 (cycle 11의 null 행은 건너뜀)
    assert.strictEqual(csvLines.length, 2, "결측 행(null)은 CSV에서 제외되어 실제 측정 행만 포함되어야 합니다.");
    assert.ok(csvLines[1].includes("55.4"), "실제 측정치 행은 정상 포함되어야 합니다.");
    assert.ok(!csvContent.includes(",null,"), "CSV에 null 문자열이 포함되면 안 됩니다.");

    // 8. cycle 140 완료 SSE 수신 시 종료 처리
    const completedSse = {
      house: "H001",
      scenario: "sensor_fault",
      status: "completed",
      sec: 140,
      cycle_count: 140,
      now_iso: "2026-09-17T00:02:20.000Z",
      simTimeKst: "09:02:20",
      measurementAvailable: true,
      sensorFault: false,
      gapElapsedSec: 120,
      gapRemainingSec: 0,
      totalP: 56.0,
      apparentS: 61.0,
      currentA: 0.28,
      voltage: 220.0,
      pf: 0.92,
      totalQ: 24.0,
      activeNames: [],
      eventNoticeText: "시나리오 완료 — 센서 고장 및 복구 시연 완료 (총 140초)"
    };
    processServerMetrics(completedSse);
    assert.strictEqual(badgeStatus.textContent, "H001 센서 결측 시연 완료", "완료 시 배지 텍스트 검증");

    // 9. cycle 140 이벤트 유형 검증: "시연 완료"여야 하며 "센서 고장"으로 오분류되지 않아야 함
    const eventTableBody = getOrCreateElement('eventTableBody');
    const latestRow = eventTableBody.children[0];
    assert.ok(latestRow, "이벤트 테이블에 완료 행이 존재해야 합니다.");
    const badgeMatch = latestRow.innerHTML.match(/<span class="badge-status[^>]*>([^<]+)<\/span>/);
    assert.ok(badgeMatch, "이벤트 유형 배지가 존재해야 합니다.");
    const eventTypeText = badgeMatch[1];
    assert.ok(eventTypeText.includes("시연 완료"), `cycle 140 이벤트 유형은 '시연 완료'여야 합니다. (실제: ${eventTypeText})`);
    assert.strictEqual(eventTypeText.includes("센서 고장"), false, "cycle 140 이벤트 유형에 '센서 고장'이 포함되면 안 됩니다.");

    console.log("✔ Test 29 통과: sensor_fault UI 연동, null-safe 렌더링, 차트 null-gap, CSV 제외 및 cycle 140 이벤트 유형(시연 완료) 검증 완료");
  }

  // ----------------------------------------------------
  // Test 30: faultDurationInput 조건부 검증 및 동적 결측 시간 UI 라이프사이클
  // ----------------------------------------------------
  {
    console.log("\n[Test 30] faultDurationInput 조건부 검증 및 동적 결측 시간 UI 라이프사이클 검증");
    const startMultiSimulation = getGlobal('startMultiSimulation');
    const resetSimulation = getGlobal('resetSimulation');
    const applyPreset = getGlobal('applyPreset');
    const processServerMetrics = getGlobal('processServerMetrics');

    const faultInput = getOrCreateElement('faultDurationInput');
    const badgeStatus = getOrCreateElement('badgeStatus');

    // 0. 화면에 가상 시간 기준 안내 문구가 실제 표시되는지 확인
    assert.ok(htmlContent.includes("(가상 시간 기준, 배속 적용)"), "HTML에 '(가상 시간 기준, 배속 적용)' 문구가 표시되어야 합니다.");

    // 1. 일반 시나리오(peak) 실행 시: faultDurationInput 값이 비어 있거나 잘못되어 있어도 차단되지 않음
    applyPreset('peak_single');
    const invalidValues = ["", "-10", "abc", "0", "99999", "12.5"];
    for (const badVal of invalidValues) {
      faultInput.value = badVal;
      fetchCalls.length = 0;
      mockFetchResponse = {
        ok: true,
        status: 200,
        json: async () => ({ status: "started" })
      };
      await startMultiSimulation();
      const startCall = fetchCalls.find(c => c.url === '/api/start');
      assert.ok(startCall, `peak 실행에서는 faultDurationInput이 '${badVal}'이어도 /api/start가 호출되어야 합니다.`);
      const reqBody = JSON.parse(startCall.options.body);
      assert.strictEqual(reqBody.fault_duration_sec, undefined, "peak 실행 시 payload에 fault_duration_sec가 포함되면 안 됩니다.");
      await resetSimulation();
    }

    // 2. sensor_fault 시나리오 실행 시: faultDurationInput이 잘못되어 있으면 실행 차단
    applyPreset('fault_single');
    for (const badVal of invalidValues) {
      faultInput.value = badVal;
      fetchCalls.length = 0;
      await startMultiSimulation();
      const startCall = fetchCalls.find(c => c.url === '/api/start');
      assert.strictEqual(startCall, undefined, `sensor_fault 실행에서는 faultDurationInput이 '${badVal}'일 때 /api/start가 호출되지 않고 차단되어야 합니다.`);
    }

    // 3. sensor_fault 시나리오 실행 시: 올바른 값(30초)일 때 payload에 fault_duration_sec 포함
    faultInput.value = "30";
    fetchCalls.length = 0;
    mockFetchResponse = {
      ok: true,
      status: 200,
      json: async () => ({ status: "started" })
    };
    await startMultiSimulation();
    const sfCall = fetchCalls.find(c => c.url === '/api/start');
    assert.ok(sfCall, "올바른 30초 설정 시 /api/start가 호출되어야 합니다.");
    const sfReq = JSON.parse(sfCall.options.body);
    assert.strictEqual(sfReq.fault_duration_sec, 30, "payload에 fault_duration_sec: 30이 포함되어야 합니다.");

    // 4. 실행 중일 때 faultDurationInput disabled 여부 확인
    assert.strictEqual(faultInput.disabled, true, "시뮬레이션 실행 중에는 faultDurationInput이 비활성화되어야 합니다.");

    // 5. SSE 수신 시 동적 30초 배지 및 상태 문구 렌더링 확인
    const sseFault30 = {
      house: "H001",
      scenario: "sensor_fault",
      status: "running",
      sec: 11,
      cycle_count: 11,
      measurementAvailable: false,
      sensorFault: true,
      faultDurationSec: 30,
      gapElapsedSec: 1,
      gapRemainingSec: 29,
      totalP: null,
      eventNoticeText: "센서 고장 시작 — 전력 계측 MQTT 메시지 발행 중단 (30초간 결측)"
    };
    processServerMetrics(sseFault30);
    assert.ok(badgeStatus.textContent.includes("1/30초"), `배지에 1/30초가 렌더링되어야 합니다. (실제: ${badgeStatus.textContent})`);
    const badgeH1 = getOrCreateElement('status_badge_H001');
    assert.ok(badgeH1.textContent.includes("1/30초"), `가구 테이블 배지에도 1/30초가 렌더링되어야 합니다. (실제: ${badgeH1.textContent})`);

    // 6. 시연 완료 SSE 수신 시 30초 완료 문구 및 입력창 재활성화 확인
    const sseDone30 = {
      house: "H001",
      scenario: "sensor_fault",
      status: "completed",
      sec: 50,
      cycle_count: 50,
      measurementAvailable: true,
      sensorFault: false,
      faultDurationSec: 30,
      gapElapsedSec: 30,
      gapRemainingSec: 0,
      totalP: 55.0,
      eventNoticeText: "시나리오 완료 — 센서 고장 및 복구 시연 완료 (총 50초)"
    };
    processServerMetrics(sseDone30);
    const timelineNotice = getOrCreateElement('timelineNotice');
    assert.ok(timelineNotice.innerHTML.includes("30초 결측 구간 종료"), `완료 안내 문구에 30초 결측이 명시되어야 합니다. (실제: ${timelineNotice.innerHTML})`);
    assert.strictEqual(faultInput.disabled, false, "시뮬레이션 완료 후 faultDurationInput이 재활성화되어야 합니다.");

    console.log("✔ Test 30 통과: faultDurationInput 조건부 검증, 비-sensor_fault 무시, sensor_fault payload 포함, 컨트롤 활성화 라이프사이클 및 동적 SSE 렌더링 검증 완료");
  }

  console.log("\n==========================================");
  console.log("🎉 모든 프런트엔드 실제 JavaScript 테스트 통과!");
  console.log("==========================================\n");
}

runTests().catch(err => {
  console.error("✘ 테스트 실행 중 오류 발생:", err);
  process.exit(1);
});
