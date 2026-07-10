using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using ClickAssistant.Application.Abstractions;
using ClickAssistant.Domain.ValueObjects;

namespace ClickAssistant.App;

/// <summary>
/// 全屏透明坐标选择层，跟随真实鼠标显示虚拟箭头并用左键确认坐标。
/// </summary>
public partial class CoordinatePickerWindow : Window
{
    private readonly ICursorPositionService cursorPositionService;

    public CoordinatePickerWindow(ICursorPositionService cursorPositionService)
    {
        this.cursorPositionService = cursorPositionService;
        InitializeComponent();
    }

    public ScreenPoint? SelectedPoint { get; private set; }

    private void HandleLoaded(object sender, RoutedEventArgs e)
    {
        Left = SystemParameters.VirtualScreenLeft;
        Top = SystemParameters.VirtualScreenTop;
        Width = SystemParameters.VirtualScreenWidth;
        Height = SystemParameters.VirtualScreenHeight;

        Activate();
        Focus();
        UpdateCursorVisual();
    }

    private void HandleMouseMove(object sender, MouseEventArgs e)
    {
        UpdateCursorVisual();
    }

    private void HandleMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        SelectedPoint = cursorPositionService.GetCurrentPosition();
        DialogResult = true;
        Close();
    }

    private void HandleKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key != Key.Escape)
        {
            return;
        }

        DialogResult = false;
        Close();
    }

    private void UpdateCursorVisual()
    {
        var screenPoint = cursorPositionService.GetCurrentPosition();
        var canvasPoint = ToCanvasPoint(screenPoint);

        VerticalGuide.X1 = canvasPoint.X;
        VerticalGuide.X2 = canvasPoint.X;
        VerticalGuide.Y1 = 0;
        VerticalGuide.Y2 = ActualHeight;

        HorizontalGuide.X1 = 0;
        HorizontalGuide.X2 = ActualWidth;
        HorizontalGuide.Y1 = canvasPoint.Y;
        HorizontalGuide.Y2 = canvasPoint.Y;

        Canvas.SetLeft(TargetRing, canvasPoint.X - TargetRing.Width / 2);
        Canvas.SetTop(TargetRing, canvasPoint.Y - TargetRing.Height / 2);
        Canvas.SetLeft(CursorShape, canvasPoint.X - 8);
        Canvas.SetTop(CursorShape, canvasPoint.Y - 4);

        CoordinateText.Text = $"X={screenPoint.X}，Y={screenPoint.Y}";
        UpdateInfoPanelPosition(canvasPoint);
    }

    private Point ToCanvasPoint(ScreenPoint screenPoint)
    {
        var point = new Point(screenPoint.X, screenPoint.Y);
        var source = PresentationSource.FromVisual(this);
        if (source?.CompositionTarget is { } compositionTarget)
        {
            point = compositionTarget.TransformFromDevice.Transform(point);
        }

        return new Point(point.X - Left, point.Y - Top);
    }

    private void UpdateInfoPanelPosition(Point canvasPoint)
    {
        var panelWidth = InfoPanel.ActualWidth > 0 ? InfoPanel.ActualWidth : InfoPanel.Width;
        var panelHeight = InfoPanel.ActualHeight > 0 ? InfoPanel.ActualHeight : 110;
        var left = canvasPoint.X + 28;
        var top = canvasPoint.Y + 28;

        if (ActualWidth > 0)
        {
            left = Math.Clamp(left, 12, Math.Max(12, ActualWidth - panelWidth - 12));
        }

        if (ActualHeight > 0)
        {
            top = Math.Clamp(top, 12, Math.Max(12, ActualHeight - panelHeight - 12));
        }

        Canvas.SetLeft(InfoPanel, left);
        Canvas.SetTop(InfoPanel, top);
    }
}
