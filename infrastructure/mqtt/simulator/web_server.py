"""
NILM 스마트홈 전력 시뮬레이터 경량 웹 컨트롤러 서버 (Web Controller Server)

브라우저 화면(waveform_viewer.html)의 버튼 조작에 따라 실제 Mosquitto MQTT 브로커로
전력 데이터를 실시간 발행(Publish)하고, 브라우저 차트와 완벽히 동기화(SSE)합니다.

실행 방법:
    python web_server.py
    python web_server.py 8089
    (브라우저가 자동으로 http://localhost:8085 로 열립니다)
"""

import asyncio
import os
import sys
import webbrowser

# Windows SelectorLoop 호환성 설정
if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
if CURRENT_DIR not in sys.path:
    sys.path.append(CURRENT_DIR)

import simulator
from server.config import DEFAULT_PORT, ALLOWED_SCENARIOS
from server.manager import SimulatorManager, ModeConflictError
from server.request_handler import ThreadedHTTPServer, RequestHandler, create_request_handler

# waveform_viewer.html 절대 경로 계산
HTML_PATH = os.path.join(CURRENT_DIR, "waveform_viewer.html")

# 전역 SimulatorManager 인스턴스 (하위 호환성 유지)
manager = SimulatorManager()
handler_class = create_request_handler(manager, HTML_PATH)


def main():
    port = DEFAULT_PORT
    if len(sys.argv) > 1 and sys.argv[1].isdigit():
        port = int(sys.argv[1])

    server_address = ("", port)
    httpd = ThreadedHTTPServer(server_address, handler_class)

    url = f"http://localhost:{port}"
    print(f"============================================================")
    print(f" NILM 전력 시뮬레이터 인터랙티브 웹 서버 가동")
    print(f" - 대시보드 URL : {url}")
    print(f" - MQTT 브로커  : {simulator.DEFAULT_BROKER_HOST}:{simulator.DEFAULT_BROKER_PORT}")
    print(f" - 기능: 화면 버튼 클릭 시 실제 MQTT 발행 및 차트 실시간 렌더링")
    print(f" - 서버 종료: Ctrl + C")
    print(f"============================================================", flush=True)

    # 기본 브라우저 자동 오픈
    try:
        webbrowser.open(url)
    except Exception:
        pass

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n웹 서버를 정지합니다.", flush=True)
        manager.stop()
        httpd.server_close()


if __name__ == "__main__":
    main()
