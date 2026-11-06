#!/bin/bash

# 배포될 경로 (우분투 기준 홈 디렉토리 하위)
REPOSITORY=~/scholarship-deploy
cd $REPOSITORY

APP_NAME=scholarship-backend
echo "> 현재 구동 중인 애플리케이션 PID 확인"
CURRENT_PID=$(pgrep -f $APP_NAME)

if [ -z "$CURRENT_PID" ]; then
  echo "> 현재 구동 중인 애플리케이션이 없으므로 종료하지 않습니다."
else
  echo "> kill -15 $CURRENT_PID"
  kill -15 $CURRENT_PID
  sleep 10
fi

echo "> 새 애플리케이션 배포"
# 가장 최근에 복사된 jar 파일 찾기 (plain jar 제외)
JAR_NAME=$(ls -tr $REPOSITORY/*.jar | grep -v plain | tail -n 1)

echo "> JAR Name: $JAR_NAME"
echo "> $JAR_NAME 에 실행권한 추가"
chmod +x $JAR_NAME

echo "> $JAR_NAME 백그라운드 실행"
# prod 프로필로 백그라운드 무중단 실행 (로그는 nohup.out에 남김)
nohup java -jar \
  -Dspring.profiles.active=prod \
  $JAR_NAME > $REPOSITORY/nohup.out 2>&1 &

echo "> 배포 스크립트 실행 완료"