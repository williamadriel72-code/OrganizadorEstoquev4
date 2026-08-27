using System;
using System.Drawing;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace AutoClickerPC
{
    internal static class Program
    {
        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }

    public class MainForm : Form
    {
        [DllImport("user32.dll")] static extern bool GetCursorPos(out POINT lpPoint);
        [DllImport("user32.dll")] static extern bool SetCursorPos(int X, int Y);
        [DllImport("user32.dll")] static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
        [DllImport("user32.dll")] static extern bool RegisterHotKey(IntPtr hWnd, int id, uint modifiers, uint vk);
        [DllImport("user32.dll")] static extern bool UnregisterHotKey(IntPtr hWnd, int id);

        struct POINT { public int X; public int Y; }

        const int WM_HOTKEY = 0x0312;
        const int HOTKEY_F8 = 1;
        const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
        const uint MOUSEEVENTF_LEFTUP = 0x0004;

        readonly Label status = new Label();
        readonly TextBox position = new TextBox();
        readonly NumericUpDown qty = new NumericUpDown();
        readonly NumericUpDown interval = new NumericUpDown();
        readonly Button start = new Button();
        readonly Button stop = new Button();
        readonly Button clear = new Button();
        readonly ProgressBar progress = new ProgressBar();
        readonly Timer timer = new Timer();
        readonly MarkerForm marker = new MarkerForm();

        bool hasPoint;
        bool running;
        int x;
        int y;
        int current;
        int target;

        public MainForm()
        {
            Text = "AutoClicker PC";
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            ClientSize = new Size(400, 380);
            Font = new Font("Segoe UI", 9F);

            var title = new Label {
                Text = "AUTOCLICKER PC",
                Font = new Font("Segoe UI", 15F, FontStyle.Bold),
                TextAlign = ContentAlignment.MiddleCenter,
                Location = new Point(20, 15),
                Size = new Size(360, 32)
            };
            Controls.Add(title);

            var help = new Label {
                Text = "Posicione o mouse e aperte F8.\nA bolinha mostra exatamente onde o clique ocorrerá.",
                TextAlign = ContentAlignment.MiddleCenter,
                Location = new Point(20, 50),
                Size = new Size(360, 45)
            };
            Controls.Add(help);

            position.Text = "Posição: nenhuma";
            position.ReadOnly = true;
            position.Location = new Point(20, 105);
            position.Size = new Size(360, 25);
            Controls.Add(position);

            Controls.Add(new Label { Text = "Quantidade:", Location = new Point(20, 150), Size = new Size(110, 22) });
            qty.Location = new Point(150, 148);
            qty.Size = new Size(230, 25);
            qty.Minimum = 1;
            qty.Maximum = 100;
            qty.Value = 100;
            Controls.Add(qty);

            Controls.Add(new Label { Text = "Intervalo (ms):", Location = new Point(20, 190), Size = new Size(120, 22) });
            interval.Location = new Point(150, 188);
            interval.Size = new Size(230, 25);
            interval.Minimum = 10;
            interval.Maximum = 60000;
            interval.Value = 100;
            interval.Increment = 10;
            Controls.Add(interval);

            start.Text = "INICIAR";
            start.Location = new Point(20, 230);
            start.Size = new Size(170, 46);
            start.Enabled = false;
            start.Click += (s, e) => StartClicks();
            Controls.Add(start);

            stop.Text = "PARAR";
            stop.Location = new Point(210, 230);
            stop.Size = new Size(170, 46);
            stop.Enabled = false;
            stop.Click += (s, e) => StopClicks("Parado");
            Controls.Add(stop);

            clear.Text = "REMOVER PONTO";
            clear.Location = new Point(20, 288);
            clear.Size = new Size(360, 36);
            clear.Enabled = false;
            clear.Click += (s, e) => ClearPoint();
            Controls.Add(clear);

            progress.Location = new Point(20, 335);
            progress.Size = new Size(360, 18);
            progress.Minimum = 0;
            progress.Maximum = 100;
            Controls.Add(progress);

            status.Text = "F8 = marcar ponto";
            status.TextAlign = ContentAlignment.MiddleCenter;
            status.Location = new Point(20, 355);
            status.Size = new Size(360, 22);
            Controls.Add(status);

            timer.Tick += Timer_Tick;
            Shown += (s, e) => RegisterHotKey(Handle, HOTKEY_F8, 0, (uint)Keys.F8);
            FormClosed += (s, e) => {
                UnregisterHotKey(Handle, HOTKEY_F8);
                timer.Stop();
                marker.Close();
            };
        }

        protected override void WndProc(ref Message m)
        {
            if (m.Msg == WM_HOTKEY && m.WParam.ToInt32() == HOTKEY_F8)
                MarkCurrentPoint();
            base.WndProc(ref m);
        }

        void MarkCurrentPoint()
        {
            POINT p;
            if (!GetCursorPos(out p)) return;
            x = p.X;
            y = p.Y;
            hasPoint = true;
            position.Text = $"Posição: X={x}  Y={y}";
            status.Text = "Ponto marcado";
            start.Enabled = !running;
            clear.Enabled = true;
            marker.SetCenter(x, y);
            marker.Show();
            marker.BringToFront();
            System.Media.SystemSounds.Asterisk.Play();
        }

        void StartClicks()
        {
            if (!hasPoint || running) return;
            target = (int)qty.Value;
            current = 0;
            running = true;
            progress.Value = 0;
            timer.Interval = (int)interval.Value;
            qty.Enabled = false;
            interval.Enabled = false;
            start.Enabled = false;
            clear.Enabled = false;
            stop.Enabled = true;
            status.Text = "Executando...";
            timer.Start();
        }

        void Timer_Tick(object sender, EventArgs e)
        {
            if (!running) return;
            if (current >= target) {
                StopClicks("Concluído");
                return;
            }

            SetCursorPos(x, y);
            mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, UIntPtr.Zero);
            mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, UIntPtr.Zero);
            current++;
            int pct = (int)Math.Floor((double)current / target * 100.0);
            progress.Value = Math.Max(0, Math.Min(100, pct));
            status.Text = $"Cliques: {current}/{target}";
            if (current >= target) StopClicks("Concluído");
        }

        void StopClicks(string text)
        {
            timer.Stop();
            running = false;
            qty.Enabled = true;
            interval.Enabled = true;
            start.Enabled = hasPoint;
            clear.Enabled = hasPoint;
            stop.Enabled = false;
            status.Text = $"{text}: {current}/{target}";
        }

        void ClearPoint()
        {
            hasPoint = false;
            marker.Hide();
            position.Text = "Posição: nenhuma";
            status.Text = "F8 = marcar ponto";
            start.Enabled = false;
            clear.Enabled = false;
        }
    }

    public sealed class MarkerForm : Form
    {
        const int WS_EX_TRANSPARENT = 0x20;
        const int WS_EX_TOOLWINDOW = 0x80;
        const int WS_EX_NOACTIVATE = 0x08000000;

        public MarkerForm()
        {
            FormBorderStyle = FormBorderStyle.None;
            ShowInTaskbar = false;
            TopMost = true;
            StartPosition = FormStartPosition.Manual;
            Size = new Size(30, 30);
            BackColor = Color.Magenta;
            TransparencyKey = Color.Magenta;
        }

        protected override CreateParams CreateParams {
            get {
                var cp = base.CreateParams;
                cp.ExStyle |= WS_EX_TRANSPARENT | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE;
                return cp;
            }
        }

        protected override bool ShowWithoutActivation => true;

        public void SetCenter(int x, int y)
        {
            Location = new Point(x - Width / 2, y - Height / 2);
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            using (var red = new SolidBrush(Color.Red))
            using (var white = new Pen(Color.White, 3F)) {
                e.Graphics.FillEllipse(red, 4, 4, 22, 22);
                e.Graphics.DrawEllipse(white, 4, 4, 22, 22);
            }
        }
    }
}
