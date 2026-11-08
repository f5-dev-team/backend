#!/bin/bash

# 배포될 경로 (우분투 기준 홈 디렉토리 하위)
REPOSITORY=~/scholarship-deploy
cd $REPOSITORY

# 깃허브 액션에서 넘겨준 파라미터($1)를 파일명으로 사용
if [ -z "$1" ]; then
    echo "> Error: 배포할 JAR 파일 이름이 파라미터로 전달되지 않았습니다."
    exit 1
fi

JAR_NAME="$REPOSITORY/$1"

echo "> 새로 배포할 JAR 파일: $JAR_NAME"

# 파일이 실제로 존재하는지 확인 (안전장치)
if [ ! -f "$JAR_NAME" ]; then
    echo "> Error: $JAR_NAME 파일을 찾을 수 없습니다. 배포를 중단합니다."
    exit 1
fi

echo "> 현재 구동중인 애플리케이션 pid 확인"
CURRENT_PID=$(pgrep -fo "java -jar.*scholarship-backend")

# 변수를 큰따옴표로 감싸서 여러 PID가 들어오더라도 문법 에러가 나지 않도록 방지
if [ -z "$CURRENT_PID" ]; then
  echo "> 현재 구동중인 애플리케이션이 없으므로 종료하지 않습니다."
else
  echo "> kill -15 $CURRENT_PID"
  kill -15 $CURRENT_PID

  # 구버전 프로세스가 포트를 완전히 반납할 때까지 최대 15초 동안 8080 포트 점유 여부 확인
  echo "> 이전 프로세스($CURRENT_PID)의 종료 및 포트 해제 대기 중..."
  for i in {1..15}
  do
    if ! ps -p $CURRENT_PID > /dev/null; then
      echo "> ✅ 이전 프로세스가 안전하게 종료되었습니다."
      break
    fi
    sleep 1
  done

  # 만약 15초가 지나도 안 죽었으면 강제 종료(-9)
  if ps -p $CURRENT_PID > /dev/null; then
    echo "> ⚠️ 이전 프로세스가 정상 종료되지 않아 강제 종료(kill -9)합니다."
    kill -9 $CURRENT_PID
    sleep 2
  fi
fi

echo "> 새 애플리케이션 배포 시작"
echo "> JAR Name: $JAR_NAME"

# 애플리케이션 실행 (prod 프로필 적용)
nohup java -jar -Dspring.profiles.active=prod $JAR_NAME > $REPOSITORY/nohup.out 2>&1 &

NEW_PID=$!
echo "> 애플리케이션 기동 대기 및 헬스 체크 시작..."

# 10초마다 1번씩, 총 6번(최대 60초) 헬스 체크 시도
for RETRY_COUNT in {1..6}
do
  # --max-time 5를 주어 5초 안에 응답이 없으면 실패로 간주하고 빠져나오도록 함
  # curl 명령어 실행 자체가 실패(네트워크 오류, 타임아웃 등)하면 RESPONSE_CODE를 000 등으로 처리
  RESPONSE_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 http://localhost:8080/actuator/health || echo "000")

  if [ "$RESPONSE_CODE" -eq 200 ]; then
    echo "> ✅ 애플리케이션 기동 완료 및 헬스 체크 성공! (PID: $NEW_PID)"
    exit 0
  fi

  # 응답이 200이 아닌데 프로세스마저 죽어버렸다면 즉시 실패 처리
  if ! ps -p $NEW_PID > /dev/null; then
    echo "> ❌ Error: 기동 중 프로세스가 비정상 종료되었습니다."
    break
  fi

  echo "> 아직 준비되지 않았습니다. (상태 코드: $RESPONSE_CODE) 10초 후 재시도... ($RETRY_COUNT/6)"
  sleep 10
done

# 60초 동안 계속 시도했는데도 200이 안 떴거나, 중간에 프로세스가 죽어서 break로 빠져나온 경우
echo "> ❌ Error: 헬스 체크 최종 실패. (60초 초과 또는 프로세스 종료)"
echo "> ==== 최근 에러 로그 (nohup.out) ===="
tail -n 20 $REPOSITORY/nohup.out
exit 1