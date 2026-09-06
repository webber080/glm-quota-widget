#!/bin/zsh
# GLM 额度菜单栏插件 — 一键构建脚本（在 Mac 上执行）
# 产物: build/GLM额度.app（可拖进「应用程序」并在登录项里自启）
set -e
cd "$(dirname "$0")"
mkdir -p build
swiftc -O -o build/GLMQuota GLMQuotaApp.swift \
    -framework AppKit -framework SwiftUI
echo "二进制已生成: build/GLMQuota"
echo "运行: ./build/GLMQuota  （菜单栏出现百分比数字）"
echo "Key 写入: mkdir -p ~/.config/glm-quota && echo '你的key' > ~/.config/glm-quota/key"
