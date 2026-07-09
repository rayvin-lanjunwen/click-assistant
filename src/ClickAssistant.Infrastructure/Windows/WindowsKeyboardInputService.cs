using System.Runtime.InteropServices;
using ClickAssistant.Application.Abstractions;

namespace ClickAssistant.Infrastructure.Windows;

/// <summary>
/// Windows 键盘输入服务，使用 SendInput 模拟单键、组合键和 Unicode 文本输入。
/// </summary>
public sealed class WindowsKeyboardInputService : IKeyboardInputService
{
    private const uint InputKeyboard = 1;
    private const uint KeyEventKeyUp = 0x0002;
    private const uint KeyEventUnicode = 0x0004;
    private static readonly IReadOnlyDictionary<string, ushort> KeyMap = CreateKeyMap();
    private static readonly IReadOnlySet<string> ModifierKeyNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
    {
        "Ctrl",
        "Control",
        "Alt",
        "Shift",
        "Win",
        "Windows",
        "Meta"
    };

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
    /// 触发一次组合键，例如 Ctrl+C、Ctrl+Shift+Esc 或 Alt+Tab。
    /// </summary>
    public Task PressShortcutAsync(
        string shortcutKeys,
        CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();

        var keys = ParseShortcutKeys(shortcutKeys);
        var modifiers = keys.Take(keys.Count - 1).ToList();
        var mainKey = keys[^1];

        try
        {
            foreach (var modifier in modifiers)
            {
                SendKeyDown(modifier);
            }

            PressVirtualKey(mainKey);
        }
        finally
        {
            for (var index = modifiers.Count - 1; index >= 0; index--)
            {
                SendKeyUp(modifiers[index]);
            }
        }

        return Task.CompletedTask;
    }

    /// <summary>
    /// 按 Unicode 字符输入文本，避免受当前键盘布局影响。
    /// </summary>
    public async Task TypeTextAsync(
        string text,
        int intervalMs,
        CancellationToken cancellationToken = default)
    {
        foreach (var character in text)
        {
            cancellationToken.ThrowIfCancellationRequested();
            PressUnicodeCharacter(character);

            if (intervalMs > 0)
            {
                await Task.Delay(intervalMs, cancellationToken);
            }
        }
    }

    /// <summary>
    /// 解析组合键文本，最后一个按键视为主按键，其余必须为修饰键。
    /// </summary>
    private static IReadOnlyList<ushort> ParseShortcutKeys(string shortcutKeys)
    {
        var keyParts = shortcutKeys
            .Split('+', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);

        if (keyParts.Length < 2)
        {
            throw new InvalidOperationException("组合键至少需要一个修饰键和一个主按键，例如 Ctrl+C。");
        }

        for (var index = 0; index < keyParts.Length - 1; index++)
        {
            if (!ModifierKeyNames.Contains(keyParts[index]))
            {
                throw new InvalidOperationException($"组合键中的“{keyParts[index]}”必须是 Ctrl、Alt、Shift 或 Win。");
            }
        }

        if (ModifierKeyNames.Contains(keyParts[^1]))
        {
            throw new InvalidOperationException("组合键最后一个按键必须是主按键，不能只是修饰键。");
        }

        return keyParts.Select(ResolveVirtualKey).ToList();
    }

    /// <summary>
    /// 将用户输入的按键名称转换为 Windows 虚拟键码。
    /// </summary>
    private static ushort ResolveVirtualKey(string keyName)
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
    private static void PressVirtualKey(ushort virtualKey)
    {
        SendKeyDown(virtualKey);
        SendKeyUp(virtualKey);
    }

    /// <summary>
    /// 发送单个 Unicode 字符。
    /// </summary>
    private static void PressUnicodeCharacter(char character)
    {
        SendKeyboardInput(character, KeyEventUnicode);
        SendKeyboardInput(character, KeyEventUnicode | KeyEventKeyUp);
    }

    /// <summary>
    /// 发送虚拟键按下事件。
    /// </summary>
    private static void SendKeyDown(ushort virtualKey)
    {
        SendKeyboardInput(virtualKey, 0);
    }

    /// <summary>
    /// 发送虚拟键抬起事件。
    /// </summary>
    private static void SendKeyUp(ushort virtualKey)
    {
        SendKeyboardInput(virtualKey, KeyEventKeyUp);
    }

    /// <summary>
    /// 调用 SendInput 并在失败时抛出系统错误。
    /// </summary>
    private static void SendKeyboardInput(ushort value, uint flags)
    {
        var input = new Input
        {
            Type = InputKeyboard,
            Data = new InputUnion
            {
                KeyboardInput = new KeyboardInput
                {
                    VirtualKey = (flags & KeyEventUnicode) != 0 ? (ushort)0 : value,
                    ScanCode = (flags & KeyEventUnicode) != 0 ? value : (ushort)0,
                    Flags = flags,
                    Time = 0,
                    ExtraInfo = UIntPtr.Zero
                }
            }
        };

        var sent = SendInput(1, [input], Marshal.SizeOf<Input>());
        if (sent != 1)
        {
            throw new InvalidOperationException("发送键盘输入失败。");
        }
    }

    /// <summary>
    /// 创建常用按键别名映射，英文和常见中文名称都可以识别。
    /// </summary>
    private static IReadOnlyDictionary<string, ushort> CreateKeyMap()
    {
        var keyMap = new Dictionary<string, ushort>(StringComparer.OrdinalIgnoreCase)
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
            ["删除"] = 0x2E,
            ["Win"] = 0x5B,
            ["Windows"] = 0x5B,
            ["Meta"] = 0x5B
        };

        for (var digit = 0; digit <= 9; digit++)
        {
            keyMap[digit.ToString()] = (ushort)(0x30 + digit);
        }

        for (var letter = 'A'; letter <= 'Z'; letter++)
        {
            keyMap[letter.ToString()] = letter;
        }

        for (var functionKey = 1; functionKey <= 24; functionKey++)
        {
            keyMap[$"F{functionKey}"] = (ushort)(0x70 + functionKey - 1);
        }

        for (var numpad = 0; numpad <= 9; numpad++)
        {
            keyMap[$"NumPad{numpad}"] = (ushort)(0x60 + numpad);
        }

        return keyMap;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint inputCount, Input[] inputs, int inputSize);

    [StructLayout(LayoutKind.Sequential)]
    private struct Input
    {
        public uint Type;
        public InputUnion Data;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)]
        public MouseInput MouseInput;

        [FieldOffset(0)]
        public KeyboardInput KeyboardInput;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MouseInput
    {
        public int X;
        public int Y;
        public uint MouseData;
        public uint Flags;
        public uint Time;
        public UIntPtr ExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KeyboardInput
    {
        public ushort VirtualKey;
        public ushort ScanCode;
        public uint Flags;
        public uint Time;
        public UIntPtr ExtraInfo;
    }
}
