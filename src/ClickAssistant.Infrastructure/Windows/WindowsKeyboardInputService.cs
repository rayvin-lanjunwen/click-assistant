using System.Runtime.InteropServices;
using ClickAssistant.Application.Abstractions;

namespace ClickAssistant.Infrastructure.Windows;

/// <summary>
/// Windows 键盘输入服务，使用虚拟键码模拟单键按下和连按。
/// </summary>
public sealed class WindowsKeyboardInputService : IKeyboardInputService
{
    private const uint KeyEventKeyUp = 0x0002;
    private static readonly IReadOnlyDictionary<string, byte> KeyMap = CreateKeyMap();

    /// <summary>
    /// 按指定次数触发单个键，连按间隔由步骤配置控制。
    /// </summary>
    public async Task PressKeyAsync(
        string keyName,
        int pressCount,
        int intervalMs,
        CancellationToken cancellationToken = default)
    {
        var virtualKey = ResolveVirtualKey(keyName);

        for (var index = 0; index < pressCount; index++)
        {
            cancellationToken.ThrowIfCancellationRequested();
            PressVirtualKey(virtualKey);

            if (index < pressCount - 1 && intervalMs > 0)
            {
                await Task.Delay(intervalMs, cancellationToken);
            }
        }
    }

    /// <summary>
    /// 将用户输入的按键名称转换为 Windows 虚拟键码。
    /// </summary>
    private static byte ResolveVirtualKey(string keyName)
    {
        var normalizedKeyName = keyName.Trim();

        if (KeyMap.TryGetValue(normalizedKeyName, out var virtualKey))
        {
            return virtualKey;
        }

        throw new InvalidOperationException($"暂不支持按键“{keyName}”，请使用 A-Z、0-9、F1-F24 或常用功能键。");
    }

    /// <summary>
    /// 发送一次按下和抬起事件，形成完整按键动作。
    /// </summary>
    private static void PressVirtualKey(byte virtualKey)
    {
        keybd_event(virtualKey, 0, 0, UIntPtr.Zero);
        keybd_event(virtualKey, 0, KeyEventKeyUp, UIntPtr.Zero);
    }

    /// <summary>
    /// 创建常用按键别名映射，英文和常见中文名称都可以识别。
    /// </summary>
    private static IReadOnlyDictionary<string, byte> CreateKeyMap()
    {
        var keyMap = new Dictionary<string, byte>(StringComparer.OrdinalIgnoreCase)
        {
            ["Backspace"] = 0x08,
            ["退格"] = 0x08,
            ["Tab"] = 0x09,
            ["Enter"] = 0x0D,
            ["Return"] = 0x0D,
            ["回车"] = 0x0D,
            ["Shift"] = 0x10,
            ["Ctrl"] = 0x11,
            ["Control"] = 0x11,
            ["Alt"] = 0x12,
            ["Pause"] = 0x13,
            ["CapsLock"] = 0x14,
            ["Esc"] = 0x1B,
            ["Escape"] = 0x1B,
            ["Space"] = 0x20,
            ["空格"] = 0x20,
            ["PageUp"] = 0x21,
            ["PageDown"] = 0x22,
            ["End"] = 0x23,
            ["Home"] = 0x24,
            ["Left"] = 0x25,
            ["左"] = 0x25,
            ["Up"] = 0x26,
            ["上"] = 0x26,
            ["Right"] = 0x27,
            ["右"] = 0x27,
            ["Down"] = 0x28,
            ["下"] = 0x28,
            ["Insert"] = 0x2D,
            ["Delete"] = 0x2E,
            ["Del"] = 0x2E,
            ["删除"] = 0x2E
        };

        for (var digit = 0; digit <= 9; digit++)
        {
            keyMap[digit.ToString()] = (byte)(0x30 + digit);
        }

        for (var letter = 'A'; letter <= 'Z'; letter++)
        {
            keyMap[letter.ToString()] = (byte)letter;
        }

        for (var functionKey = 1; functionKey <= 24; functionKey++)
        {
            keyMap[$"F{functionKey}"] = (byte)(0x70 + functionKey - 1);
        }

        for (var numpad = 0; numpad <= 9; numpad++)
        {
            keyMap[$"NumPad{numpad}"] = (byte)(0x60 + numpad);
        }

        return keyMap;
    }

    [DllImport("user32.dll")]
    private static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);
}
