using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Shapes;
using ClickAssistant.Domain.Entities;
using ClickAssistant.Domain.Enums;

namespace ClickAssistant.App;

/// <summary>
/// 全屏可视化坐标选择层。
/// 1. 传入 targetStep 时，点击空白区域可直接将该步骤坐标设为点击位置并关闭窗口。
/// 2. 点击已有步骤标记可快速切换选中步骤。
/// 3. 拖动标记可调整步骤坐标。
/// 4. 按 Esc 取消并关闭。
/// </summary>
public partial class CoordinatePickerWindow : Window
{
    private readonly List<ClickStep> steps;
    private readonly ClickStep? targetStep;
    private readonly Action<ClickStep>? onStepClicked;
    private readonly Dictionary<ClickStep, MarkerElements> markerMap = [];

    private bool isDragging;
    private ClickStep? draggingStep;
    private MarkerElements? draggingMarker;

    public CoordinatePickerWindow(
        List<ClickStep> steps,
        ClickStep? targetStep = null,
        Action<ClickStep>? onStepClicked = null)
    {
        this.steps = steps;
        this.targetStep = targetStep;
        this.onStepClicked = onStepClicked;
        InitializeComponent();
    }

    public ClickStep? HighlightedStep { get; set; }

    /// <summary>
    /// 标识本次选择过程中是否有步骤坐标发生变更（拖动或空白区域点击）。
    /// </summary>
    public bool IsCoordinateChanged { get; private set; }

    private void HandleLoaded(object sender, RoutedEventArgs e)
    {
        Left = SystemParameters.VirtualScreenLeft;
        Top = SystemParameters.VirtualScreenTop;
        Width = SystemParameters.VirtualScreenWidth;
        Height = SystemParameters.VirtualScreenHeight;

        Activate();
        Focus();
        DrawAllMarkers();
    }

    private void DrawAllMarkers()
    {
        OverlayCanvas.Children.Clear();
        markerMap.Clear();

        foreach (var step in steps)
        {
            if (!step.Enabled)
            {
                continue;
            }

            DrawStepMarker(step);
        }
    }

    private void DrawStepMarker(ClickStep step)
    {
        var marker = new MarkerElements();

        switch (step.ActionType)
        {
            case InputActionType.MouseClick:
                DrawClickMarker(step, marker);
                break;
            case InputActionType.Swipe:
                DrawSwipeMarker(step, marker);
                break;
            case InputActionType.TextInput:
                DrawTextInputMarker(step, marker);
                break;
        }

        markerMap[step] = marker;
    }

    private void DrawClickMarker(ClickStep step, MarkerElements marker)
    {
        var isHighlighted = HighlightedStep?.Id == step.Id;
        var color = isHighlighted ? Color.FromRgb(37, 99, 235) : Color.FromRgb(59, 130, 246);
        var fillAlpha = isHighlighted ? (byte)50 : (byte)25;

        // Circle marker
        var circle = new Ellipse
        {
            Width = 40,
            Height = 40,
            Stroke = new SolidColorBrush(color),
            StrokeThickness = 3,
            Fill = new SolidColorBrush(Color.FromArgb(fillAlpha, color.R, color.G, color.B)),
            Cursor = Cursors.Hand
        };
        Panel.SetZIndex(circle, 10);
        OverlayCanvas.Children.Add(circle);

        // Step number label
        var text = new TextBlock
        {
            Text = (step.Order + 1).ToString(),
            FontSize = 16,
            FontWeight = FontWeights.Bold,
            Foreground = new SolidColorBrush(color),
            TextAlignment = TextAlignment.Center,
            Width = 40,
            Height = 40,
            IsHitTestVisible = false
        };
        Panel.SetZIndex(text, 11);
        OverlayCanvas.Children.Add(text);

        // Type icon (click = hand)
        var icon = new TextBlock
        {
            Text = "⚬",
            FontSize = 10,
            Foreground = new SolidColorBrush(Colors.White),
            TextAlignment = TextAlignment.Center,
            Width = 14,
            Height = 14,
            IsHitTestVisible = false,
            Background = new SolidColorBrush(color),
            Clip = CreateCircleClip(7)
        };
        Panel.SetZIndex(icon, 12);
        OverlayCanvas.Children.Add(icon);

        marker.Circle = circle;
        marker.NumberText = text;
        marker.IconText = icon;
        marker.Step = step;

        PositionMarker(step, marker);

        // Mouse events for drag
        circle.MouseLeftButtonDown += (s, e) => StartDrag(step, marker, e);
        circle.MouseLeftButtonUp += (s, e) => StopDrag(e);
        circle.MouseMove += (s, e) => OnDrag(e);
    }

    private void DrawSwipeMarker(ClickStep step, MarkerElements marker)
    {
        var isHighlighted = HighlightedStep?.Id == step.Id;
        var color = isHighlighted ? Color.FromRgb(37, 99, 235) : Color.FromRgb(22, 160, 64);
        var fillAlpha = isHighlighted ? (byte)50 : (byte)25;

        // Direction line
        var line = new Line
        {
            Stroke = new SolidColorBrush(color),
            StrokeThickness = 2,
            StrokeDashArray = [6, 3],
            IsHitTestVisible = false
        };
        Panel.SetZIndex(line, 5);
        OverlayCanvas.Children.Add(line);

        // Arrow head (small triangle)
        var arrow = new Polygon
        {
            Stroke = new SolidColorBrush(color),
            StrokeThickness = 1.5,
            Fill = new SolidColorBrush(Color.FromArgb(fillAlpha, color.R, color.G, color.B)),
            IsHitTestVisible = false
        };
        Panel.SetZIndex(arrow, 6);
        OverlayCanvas.Children.Add(arrow);

        // Start circle
        var startCircle = new Ellipse
        {
            Width = 40,
            Height = 40,
            Stroke = new SolidColorBrush(color),
            StrokeThickness = 3,
            Fill = new SolidColorBrush(Color.FromArgb(fillAlpha, color.R, color.G, color.B)),
            Cursor = Cursors.Hand
        };
        Panel.SetZIndex(startCircle, 10);
        OverlayCanvas.Children.Add(startCircle);

        // End circle
        var endCircle = new Ellipse
        {
            Width = 34,
            Height = 34,
            Stroke = new SolidColorBrush(color),
            StrokeThickness = 2,
            Fill = new SolidColorBrush(Color.FromArgb((byte)15, color.R, color.G, color.B)),
            StrokeDashArray = [3, 2],
            Cursor = Cursors.Hand
        };
        Panel.SetZIndex(endCircle, 9);
        OverlayCanvas.Children.Add(endCircle);

        // Number on start circle
        var text = new TextBlock
        {
            Text = (step.Order + 1).ToString(),
            FontSize = 16,
            FontWeight = FontWeights.Bold,
            Foreground = new SolidColorBrush(color),
            TextAlignment = TextAlignment.Center,
            Width = 40,
            Height = 40,
            IsHitTestVisible = false
        };
        Panel.SetZIndex(text, 11);
        OverlayCanvas.Children.Add(text);

        // Direction icon
        var icon = new TextBlock
        {
            Text = "→",
            FontSize = 10,
            Foreground = new SolidColorBrush(Colors.White),
            TextAlignment = TextAlignment.Center,
            Width = 14,
            Height = 14,
            IsHitTestVisible = false,
            Background = new SolidColorBrush(color),
            Clip = CreateCircleClip(7)
        };
        Panel.SetZIndex(icon, 12);
        OverlayCanvas.Children.Add(icon);

        marker.Circle = startCircle;
        marker.EndCircle = endCircle;
        marker.Line = line;
        marker.Arrow = arrow;
        marker.NumberText = text;
        marker.IconText = icon;
        marker.Step = step;

        PositionSwipeMarker(step, marker);

        startCircle.MouseLeftButtonDown += (s, e) => StartDrag(step, marker, e, isStart: true);
        startCircle.MouseLeftButtonUp += (s, e) => StopDrag(e);
        startCircle.MouseMove += (s, e) => OnDrag(e);
        endCircle.MouseLeftButtonDown += (s, e) => StartDrag(step, marker, e, isStart: false);
        endCircle.MouseLeftButtonUp += (s, e) => StopDrag(e);
        endCircle.MouseMove += (s, e) => OnDrag(e);
    }

    private void DrawTextInputMarker(ClickStep step, MarkerElements marker)
    {
        var isHighlighted = HighlightedStep?.Id == step.Id;
        var color = isHighlighted ? Color.FromRgb(37, 99, 235) : Color.FromRgb(249, 115, 22);
        var fillAlpha = isHighlighted ? (byte)50 : (byte)25;

        // Circle marker
        var circle = new Ellipse
        {
            Width = 40,
            Height = 40,
            Stroke = new SolidColorBrush(color),
            StrokeThickness = 3,
            Fill = new SolidColorBrush(Color.FromArgb(fillAlpha, color.R, color.G, color.B)),
            Cursor = Cursors.Hand
        };
        Panel.SetZIndex(circle, 10);
        OverlayCanvas.Children.Add(circle);

        // Step number
        var text = new TextBlock
        {
            Text = (step.Order + 1).ToString(),
            FontSize = 16,
            FontWeight = FontWeights.Bold,
            Foreground = new SolidColorBrush(color),
            TextAlignment = TextAlignment.Center,
            Width = 40,
            Height = 40,
            IsHitTestVisible = false
        };
        Panel.SetZIndex(text, 11);
        OverlayCanvas.Children.Add(text);

        // Keyboard icon
        var icon = new TextBlock
        {
            Text = "⌨",
            FontSize = 9,
            Foreground = new SolidColorBrush(Colors.White),
            TextAlignment = TextAlignment.Center,
            Width = 14,
            Height = 14,
            IsHitTestVisible = false,
            Background = new SolidColorBrush(color),
            Clip = CreateCircleClip(7)
        };
        Panel.SetZIndex(icon, 12);
        OverlayCanvas.Children.Add(icon);

        marker.Circle = circle;
        marker.NumberText = text;
        marker.IconText = icon;
        marker.Step = step;

        PositionMarker(step, marker);

        circle.MouseLeftButtonDown += (s, e) => StartDrag(step, marker, e);
        circle.MouseLeftButtonUp += (s, e) => StopDrag(e);
        circle.MouseMove += (s, e) => OnDrag(e);
    }

    private void PositionMarker(ClickStep step, MarkerElements marker)
    {
        var canvasPoint = ScreenToCanvasPoint(new Point(step.X, step.Y));
        Canvas.SetLeft(marker.Circle!, canvasPoint.X - 20);
        Canvas.SetTop(marker.Circle!, canvasPoint.Y - 20);
        Canvas.SetLeft(marker.NumberText!, canvasPoint.X - 20);
        Canvas.SetTop(marker.NumberText!, canvasPoint.Y - 20);
        Canvas.SetLeft(marker.IconText!, canvasPoint.X + 12);
        Canvas.SetTop(marker.IconText!, canvasPoint.Y - 20);
    }

    private void PositionSwipeMarker(ClickStep step, MarkerElements marker)
    {
        var startPoint = ScreenToCanvasPoint(new Point(step.X, step.Y));
        var endPoint = ScreenToCanvasPoint(new Point(step.EndX, step.EndY));

        // Start circle
        Canvas.SetLeft(marker.Circle!, startPoint.X - 20);
        Canvas.SetTop(marker.Circle!, startPoint.Y - 20);

        // End circle
        Canvas.SetLeft(marker.EndCircle!, endPoint.X - 17);
        Canvas.SetTop(marker.EndCircle!, endPoint.Y - 17);

        // Number text on start
        Canvas.SetLeft(marker.NumberText!, startPoint.X - 20);
        Canvas.SetTop(marker.NumberText!, startPoint.Y - 20);
        Canvas.SetLeft(marker.IconText!, startPoint.X + 12);
        Canvas.SetTop(marker.IconText!, startPoint.Y - 20);

        // Direction line
        marker.Line!.X1 = startPoint.X;
        marker.Line!.Y1 = startPoint.Y;
        marker.Line!.X2 = endPoint.X;
        marker.Line!.Y2 = endPoint.Y;

        // Arrow
        DrawArrowHead(marker.Arrow!, startPoint, endPoint);
    }

    private static void DrawArrowHead(Polygon arrow, Point start, Point end)
    {
        var dx = end.X - start.X;
        var dy = end.Y - start.Y;
        var length = Math.Sqrt(dx * dx + dy * dy);
        if (length < 1) return;

        var ux = dx / length;
        var uy = dy / length;
        var size = 12;

        var midX = (start.X + end.X) / 2;
        var midY = (start.Y + end.Y) / 2;

        arrow.Points = new PointCollection
        {
            new Point(midX + ux * size, midY + uy * size),
            new Point(midX - ux * size + uy * size / 2, midY - uy * size - ux * size / 2),
            new Point(midX - ux * size - uy * size / 2, midY - uy * size + ux * size / 2)
        };
    }

    private void StartDrag(ClickStep step, MarkerElements marker, MouseButtonEventArgs e, bool isStart = true)
    {
        if (e.ClickCount > 1)
        {
            return; // Double-click
        }

        isDragging = true;
        draggingStep = step;
        draggingMarker = marker;
        draggingMarker.IsDraggingStart = isStart;

        OverlayCanvas.CaptureMouse();
        e.Handled = true;
    }

    private void StopDrag(MouseButtonEventArgs e)
    {
        isDragging = false;
        draggingStep = null;
        draggingMarker = null;
        OverlayCanvas.ReleaseMouseCapture();
    }

    private void OnDrag(MouseEventArgs e)
    {
        if (!isDragging || draggingStep is null || draggingMarker is null)
        {
            return;
        }

        var canvasPoint = e.GetPosition(OverlayCanvas);
        var screenPoint = CanvasToScreenPoint(canvasPoint);

        if (draggingStep.ActionType == InputActionType.Swipe && !draggingMarker.IsDraggingStart)
        {
            draggingStep.EndX = (int)screenPoint.X;
            draggingStep.EndY = (int)screenPoint.Y;
        }
        else
        {
            draggingStep.X = (int)screenPoint.X;
            draggingStep.Y = (int)screenPoint.Y;
        }

        IsCoordinateChanged = true;

        // Redraw this marker
        UpdateMarkerPosition(draggingStep, draggingMarker);
    }

    private void UpdateMarkerPosition(ClickStep step, MarkerElements marker)
    {
        if (step.ActionType == InputActionType.Swipe)
        {
            PositionSwipeMarker(step, marker);
        }
        else
        {
            PositionMarker(step, marker);
        }
    }

    private void HandleMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (isDragging)
        {
            return;
        }

        var canvasPoint = e.GetPosition(OverlayCanvas);
        var screenPoint = CanvasToScreenPoint(canvasPoint);

        // 优先检测是否点击了已有步骤标记
        ClickStep? clickedStep = null;
        foreach (var (step, marker) in markerMap)
        {
            if (IsPointInMarker(screenPoint, step))
            {
                clickedStep = step;
                break;
            }
        }

        if (clickedStep is not null)
        {
            HighlightedStep = clickedStep;
            onStepClicked?.Invoke(clickedStep);
            DrawAllMarkers();
            e.Handled = true;
            return;
        }

        // 点击空白区域：若存在 targetStep，则将其坐标设为点击位置并关闭窗口
        if (targetStep is not null)
        {
            targetStep.X = (int)screenPoint.X;
            targetStep.Y = (int)screenPoint.Y;
            IsCoordinateChanged = true;
            onStepClicked?.Invoke(targetStep);
            Close();
            e.Handled = true;
        }
    }

    private static bool IsPointInMarker(Point screenPoint, ClickStep step)
    {
        var center = step.ActionType == InputActionType.Swipe
            ? (new Point(step.X, step.Y), new Point(step.EndX, step.EndY))
            : (new Point(step.X, step.Y), new Point(step.X, step.Y));

        return Distance(screenPoint, center.Item1) < 30
            || (step.ActionType == InputActionType.Swipe && Distance(screenPoint, center.Item2) < 25);
    }

    private static double Distance(Point a, Point b)
    {
        var dx = a.X - b.X;
        var dy = a.Y - b.Y;
        return Math.Sqrt(dx * dx + dy * dy);
    }

    private void HandleMouseMove(object sender, MouseEventArgs e)
    {
        if (!isDragging)
        {
            return;
        }

        OnDrag(e);
    }

    private void HandleMouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        if (isDragging)
        {
            StopDrag(e);
        }
    }

    private void HandleKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape)
        {
            Close();
        }
    }

    private Point ScreenToCanvasPoint(Point screenPoint)
    {
        var source = PresentationSource.FromVisual(this);
        if (source?.CompositionTarget is { } compositionTarget)
        {
            var devicePoint = compositionTarget.TransformFromDevice.Transform(screenPoint);
            return new Point(devicePoint.X - Left, devicePoint.Y - Top);
        }

        return new Point(screenPoint.X - Left, screenPoint.Y - Top);
    }

    private Point CanvasToScreenPoint(Point canvasPoint)
    {
        var rawPoint = new Point(canvasPoint.X + Left, canvasPoint.Y + Top);
        var source = PresentationSource.FromVisual(this);
        if (source?.CompositionTarget is { } compositionTarget)
        {
            return compositionTarget.TransformToDevice.Transform(rawPoint);
        }

        return rawPoint;
    }

    private static EllipseGeometry CreateCircleClip(double radius)
    {
        return new EllipseGeometry(new Point(radius, radius), radius, radius);
    }

    /// <summary>
    /// 重新刷新所有标记（支持外部更新步骤数据后重绘）。
    /// </summary>
    public void RefreshMarkers()
    {
        DrawAllMarkers();
    }

    private sealed class MarkerElements
    {
        public ClickStep? Step { get; set; }
        public Ellipse? Circle { get; set; }
        public Ellipse? EndCircle { get; set; }
        public Line? Line { get; set; }
        public Polygon? Arrow { get; set; }
        public TextBlock? NumberText { get; set; }
        public TextBlock? IconText { get; set; }
        public bool IsDraggingStart { get; set; } = true;
    }
}
