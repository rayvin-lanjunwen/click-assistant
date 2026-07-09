using System.Runtime.InteropServices;
using ClickAssistant.Application.Abstractions;

namespace ClickAssistant.Infrastructure.Windows;

/// <summary>
/// 基于 Windows RegisterHotKey API 的全局快捷键服务。
/// </summary>
public sealed class WindowsGlobalHotkeyService : IGlobalHotkeyService
{
    private const int StopHotkeyId = 1001;
    private const int WmHotkey = 0x0312;
    private const uint ModAlt = 0x0001;
    private const uint ModControl = 0x0002;
    private const uint ModShift = 0x0004;
    private const uint ModWin = 0x0008;
    private const uint VirtualKeyS = 0x53;
    private static readonly HotkeyDefinition DefaultStopHotkey = new(ModControl | ModAlt, VirtualKeyS, "Ctrl + Alt + S");
    private IntPtr registeredWindowHandle;
    private HotkeyDefinition stopHotkey = DefaultStopHotkey;

    public event EventHandler? StopRequested;

    public string StopHotkeyText => stopHotkey.DisplayText;

    /// <summary>
    /// 注册全局立即停止快捷键，注册失败通常代表快捷键被其他程序占用。
    /// </summary>
    public HotkeyRegistrationResult RegisterStopHotkey(IntPtr windowHandle, string hotkeyText)
    {
        if (windowHandle == IntPtr.Zero)
        {
            return new HotkeyRegistrationResult(false, StopHotkeyText, "全局停止快捷键注册失败：窗口句柄无效。");
        }

        if (!TryParseHotkey(hotkeyText, out var nextHotkey, out var errorMessage))
        {
            return new HotkeyRegistrationResult(false, StopHotkeyText, $"全局停止快捷键格式无效：{errorMessage}");
        }

        var previousWindowHandle = registeredWindowHandle;
        var previousHotkey = stopHotkey;

        if (previousWindowHandle != IntPtr.Zero)
        {
            UnregisterHotKey(previousWindowHandle, StopHotkeyId);
            registeredWindowHandle = IntPtr.Zero;
        }

        if (!RegisterHotKey(windowHandle, StopHotkeyId, nextHotkey.Modifiers, nextHotkey.VirtualKey))
        {
            var restored = RestorePreviousHotkey(previousWindowHandle, previousHotkey);
            var message = restored
                ? $"全局停止快捷键注册失败：{nextHotkey.DisplayText} 可能已被占用，当前仍为 {StopHotkeyText}。"
                : $"全局停止快捷键注册失败：{nextHotkey.DisplayText} 可能已被占用，旧快捷键也恢复失败。";
            return new HotkeyRegistrationResult(
                false,
                StopHotkeyText,
                message);
        }

        registeredWindowHandle = windowHandle;
        stopHotkey = nextHotkey;
        return new HotkeyRegistrationResult(true, StopHotkeyText, $"全局停止快捷键：{StopHotkeyText}");
    }

    /// <summary>
    /// 注销全局立即停止快捷键，窗口关闭时必须调用。
    /// </summary>
    public void UnregisterStopHotkey(IntPtr windowHandle)
    {
        if (windowHandle == IntPtr.Zero)
        {
            return;
        }

        UnregisterHotKey(windowHandle, StopHotkeyId);

        if (registeredWindowHandle == windowHandle)
        {
            registeredWindowHandle = IntPtr.Zero;
        }
    }

    /// <summary>
    /// 识别 WM_HOTKEY 消息并发布立即停止事件。
    /// </summary>
    public bool ProcessWindowMessage(IntPtr windowHandle, int message, IntPtr hotkeyId)
    {
        if (message != WmHotkey || hotkeyId.ToInt32() != StopHotkeyId || windowHandle != registeredWindowHandle)
        {
            return false;
        }

        StopRequested?.Invoke(this, EventArgs.Empty);
        return true;
    }

    /// <summary>
    /// 释放时兜底注销快捷键，避免进程退出前残留注册。
    /// </summary>
    public void Dispose()
    {
        if (registeredWindowHandle != IntPtr.Zero)
        {
            UnregisterStopHotkey(registeredWindowHandle);
        }
    }

    /// <summary>
    /// 新快捷键注册失败时恢复旧快捷键，尽量保持紧急停止能力可用。
    /// </summary>
    private bool RestorePreviousHotkey(IntPtr previousWindowHandle, HotkeyDefinition previousHotkey)
    {
        if (previousWindowHandle == IntPtr.Zero)
        {
            return false;
        }

        if (RegisterHotKey(previousWindowHandle, StopHotkeyId, previousHotkey.Modifiers, previousHotkey.VirtualKey))
        {
            registeredWindowHandle = previousWindowHandle;
            stopHotkey = previousHotkey;
            return true;
        }

        return false;
    }

    /// <summary>
    /// 解析用户输入的快捷键文本。
    /// </summary>
    private static bool TryParseHotkey(
        string hotkeyText,
        out HotkeyDefinition hotkey,
        out string errorMessage)
    {
        hotkey = DefaultStopHotkey;
        errorMessage = string.Empty;

        var parts = hotkeyText
            .Split('+', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);

        if (parts.Length < 2)
        {
            errorMessage = "请使用类似 Ctrl + Alt + S 的格式。";
            return false;
        }

        uint modifiers = 0;
        string? keyDisplayText = null;
        uint virtualKey = 0;

        foreach (var part in parts)
        {
            if (TryParseModifier(part, out var modifier))
            {
                modifiers |= modifier;
                continue;
            }

            if (keyDisplayText is not null)
            {
                errorMessage = "只能包含一个主按键。";
                return false;
            }

            if (!TryParseVirtualKey(part, out virtualKey, out keyDisplayText))
            {
                errorMessage = $"暂不支持按键 {part}。";
                return false;
            }
        }

        if (modifiers == 0)
        {
            errorMessage = "至少需要包含 Ctrl、Alt、Shift 或 Win 中的一个修饰键。";
            return false;
        }

        if (keyDisplayText is null)
        {
            errorMessage = "缺少主按键。";
            return false;
        }

        hotkey = new HotkeyDefinition(modifiers, virtualKey, FormatHotkeyText(modifiers, keyDisplayText));
        return true;
    }

    /// <summary>
    /// 解析修饰键。
    /// </summary>
    private static bool TryParseModifier(string value, out uint modifier)
    {
        modifier = value.Trim().ToUpperInvariant() switch
        {
            "CTRL" or "CONTROL" => ModControl,
            "ALT" => ModAlt,
            "SHIFT" => ModShift,
            "WIN" or "WINDOWS" or "META" => ModWin,
            _ => 0
        };

        return modifier != 0;
    }

    /// <summary>
    /// 解析主按键到 Windows 虚拟键码。
    /// </summary>
    private static bool TryParseVirtualKey(string value, out uint virtualKey, out string displayText)
    {
        var normalizedValue = value.Trim().ToUpperInvariant();
        virtualKey = 0;
        displayText = normalizedValue;

        if (normalizedValue.Length == 1)
        {
            var key = normalizedValue[0];
            if ((key >= 'A' && key <= 'Z') || (key >= '0' && key <= '9'))
            {
                virtualKey = key;
                displayText = key.ToString();
                return true;
            }
        }

        if (normalizedValue.StartsWith('F')
            && int.TryParse(normalizedValue[1..], out var functionKeyNumber)
            && functionKeyNumber is >= 1 and <= 24)
        {
            virtualKey = (uint)(0x70 + functionKeyNumber - 1);
            displayText = $"F{functionKeyNumber}";
            return true;
        }

        (virtualKey, displayText) = normalizedValue switch
        {
            "ENTER" or "RETURN" => (0x0Du, "Enter"),
            "SPACE" => (0x20u, "Space"),
            "TAB" => (0x09u, "Tab"),
            "ESC" or "ESCAPE" => (0x1Bu, "Esc"),
            "DELETE" or "DEL" => (0x2Eu, "Delete"),
            "BACKSPACE" or "BACK" => (0x08u, "Backspace"),
            "UP" or "ARROWUP" => (0x26u, "Up"),
            "DOWN" or "ARROWDOWN" => (0x28u, "Down"),
            "LEFT" or "ARROWLEFT" => (0x25u, "Left"),
            "RIGHT" or "ARROWRIGHT" => (0x27u, "Right"),
            _ => (0u, normalizedValue)
        };

        return virtualKey != 0;
    }

    /// <summary>
    /// 统一快捷键展示顺序。
    /// </summary>
    private static string FormatHotkeyText(uint modifiers, string keyDisplayText)
    {
        var parts = new List<string>();

        if ((modifiers & ModControl) != 0)
        {
            parts.Add("Ctrl");
        }

        if ((modifiers & ModAlt) != 0)
        {
            parts.Add("Alt");
        }

        if ((modifiers & ModShift) != 0)
        {
            parts.Add("Shift");
        }

        if ((modifiers & ModWin) != 0)
        {
            parts.Add("Win");
        }

        parts.Add(keyDisplayText);
        return string.Join(" + ", parts);
    }

    private sealed record HotkeyDefinition(uint Modifiers, uint VirtualKey, string DisplayText);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool UnregisterHotKey(IntPtr hWnd, int id);
}
