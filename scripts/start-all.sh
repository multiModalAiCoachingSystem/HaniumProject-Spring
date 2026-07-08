#!/usr/bin/env bash
# 역할: 로컬 개발 중 관련 서비스를 실행하기 위한 편의 스크립트입니다.
docker compose -f infra/docker/docker-compose.yml up --build
