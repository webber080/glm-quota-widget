// GLM 额度 — macOS 菜单栏插件
// 显示智谱 GLM Coding Plan 5 小时限额已用比例，点击查看明细/手动刷新
import SwiftUI
import Combine

struct QuotaWindow: Identifiable {
    let id = UUID()
    let label: String
    let usedPct: Int
    let resetHours: Double
}

struct QuotaResponse: Codable {
    struct Limit: Codable {
        let type: String
        let unit: Int
        let number: Int?
        let percentage: Int
        let nextResetTime: Double?
    }
    struct Data: Codable {
        let limits: [Limit]?
        let level: String?
    }
    let code: Int
    let msg: String?
    let success: Bool
    let data: Data?
}

final class QuotaStore: ObservableObject {
    @Published var menuText: String = "…"          // 菜单栏主文本
    @Published var detail: String = "查询中…"
    @Published var windows: [QuotaWindow] = []
    @Published var level: String = ""

    static let apiKeyURL = FileManager.default.homeDirectoryForCurrentUser
        .appendingPathComponent(".config/glm-quota/key")

    static func loadKey() -> String {
        (try? String(contentsOf: apiKeyURL, encoding: .utf8))?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    func refresh() {
        let key = Self.loadKey()
        guard !key.isEmpty else {
            menuText = "GLM ⚠︎"
            detail = "未配置 API Key\n请写入 ~/.config/glm-quota/key"
            return
        }
        guard let url = URL(string: "https://open.bigmodel.cn/api/monitor/usage/quota/limit") else { return }
        var req = URLRequest(url: url)
        req.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        req.timeoutInterval = 10

        URLSession.shared.dataTask(with: req) { [weak self] data, resp, err in
            guard let self = self else { return }
            DispatchQueue.main.async {
                var apiMsg: String? = nil
                var limits: [QuotaResponse.Limit]? = nil
                var level = "unknown"
                if let data = data, let decoded = try? JSONDecoder().decode(QuotaResponse.self, from: data) {
                    apiMsg = decoded.msg
                    if decoded.success { limits = decoded.data?.limits; level = decoded.data?.level ?? "unknown" }
                }
                guard let list = limits, !list.isEmpty else {
                    self.menuText = "GLM ERR"
                    self.detail = "查询失败: \(err?.localizedDescription ?? apiMsg ?? "HTTP 错误")"
                    return
                }
                self.level = level
                var out: [QuotaWindow] = []
                for lim in list {
                    let label: String
                    switch lim.unit {
                    case 3: label = "\(lim.number ?? 5)小时"
                    case 6: label = "每周"
                    default: label = "unit\(lim.unit)"
                    }
                    let resetIn = max(0, ((lim.nextResetTime ?? 0) - Date().timeIntervalSince1970 * 1000)) / 3_600_000
                    out.append(QuotaWindow(label: label, usedPct: lim.percentage, resetHours: resetIn))
                }
                self.windows = out
                let five = out.first { $0.label.contains("小时") } ?? out[0]
                self.menuText = "\(five.usedPct)%"
                self.detail = out.map { String(format: "%@: 已用 %d%% · %.1fh 后重置", $0.label, $0.usedPct, $0.resetHours) }
                    .joined(separator: "\n") + "\n套餐: \(self.level)"
            }
        }.resume()
    }
}

@main
struct GLMQuotaApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    var body: some Scene { Settings { EmptyView() } }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private var store = QuotaStore()
    private var timer: Timer?
    private var cancellables = Set<AnyCancellable>()

    func applicationDidFinishLaunching(_ notification: Notification) {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)

        let menu = NSMenu()
        let detailItem = NSMenuItem(title: "查询中…", action: nil, keyEquivalent: "")
        detailItem.isEnabled = false
        menu.addItem(detailItem)
        menu.addItem(.separator())
        let refreshItem = NSMenuItem(title: "立即刷新", action: #selector(doRefresh), keyEquivalent: "r")
        refreshItem.target = self
        menu.addItem(refreshItem)
        let quitItem = NSMenuItem(title: "退出", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        menu.addItem(quitItem)
        statusItem.menu = menu
        statusItem.button?.title = " …"

        store.objectWillChange.sink { [weak self] _ in
            DispatchQueue.main.async {
                self?.statusItem.button?.title = " \(self?.store.menuText ?? "")"
                detailItem.title = self?.store.detail ?? ""
            }
        }.store(in: &cancellables)

        store.refresh()
        timer = Timer.scheduledTimer(withTimeInterval: 300, repeats: true) { [weak self] _ in
            self?.store.refresh()
        }
    }
    @objc private func doRefresh() { store.refresh() }
}
