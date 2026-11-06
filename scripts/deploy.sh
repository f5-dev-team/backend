#!/bin/bash

# 배포될 경로 (우분투 기준 홈 디렉토리 하위)
REPOSITORY=~/scholarship-deploy
cd $REPOSITORY

echo "> 새로 배포할 JAR 파일 탐색"
JAR_NAME=$(ls -tr $REPOSITORY/*-SNAPSHOT.jar | tail -n 1)

echo "> JAR Name: $JAR_NAME"

if [ -z "$JAR_NAME" ]; then
    echo "> Error: 배포할 JAR 파일을 찾을 수 없습니다. 배포를 중단합니다."
    exit 1
fi

echo "> 현재 구동중인 애플리케이션 pid 확인"
CURRENT_PID=$(pgrep -f scholarship-backend)

if [ -z $CURRENT_PID ]
then
  echo "> 현재 구동중인 애플리케이션이 없으므로 종료하지 않습니다."
else
  echo "> kill -15 $CURRENT_PID"
  kill -15 $CURRENT_PID
  sleep 5
fi

echo "> 새 애플리케이션 배포 시작"
echo "> JAR Name: $JAR_NAME"

# 애플리케이션 실행 (prod 프로필 적용)
nohup java -jar -Dspring.profiles.active=prod $JAR_NAME > $REPOSITORY/nohup.out 2>&1 &

# 방금 백그라운드로 실행시킨 자바 프로세스의 PID를 캡처
NEW_PID=$!

echo "> 애플리케이션 기동 대기 중 (15초)..."
sleep 15

echo "> 기동 성공 여부 확인 (Health Check)"
# ps 명령어로 해당 PID가 여전히 살아있는지 확인
if ps -p $NEW_PID > /dev/null
then
  echo "> ✅ 배포 및 기동 성공! (PID: $NEW_PID)"
else
  echo "> ❌ Error: 애플리케이션 기동에 실패했습니다. (프로세스 종료됨)"
  echo "> ==== 최근 에러 로그 (nohup.out) ===="
  tail -n 20 $REPOSITORY/nohup.out
  exit 1  # 스크립트를 에러 상태로 종료하여 깃허브 액션에 실패를 알림
fi