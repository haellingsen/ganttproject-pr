// Single-file launcher for the portable GanttProject-PR package.
//
// The jpackage app image (exe, app files and Java runtime) is embedded as a zip resource. On the first start
// the launcher unpacks it to %LOCALAPPDATA%\GanttProject-PR\<build>, later starts run the unpacked copy directly.
// A launcher from a newer build unpacks its own copy and removes the old ones.
//
// Built by make-single-exe.ps1. Uses only what comes with Windows (.NET Framework 4).
using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Windows.Forms;

static class Launcher
{
    const string AppName = "GanttProject-PR";
    const string PayloadResource = "payload.zip";
    const string BuildResource = "build.txt";
    const string DoneMarker = ".unpacked";

    [STAThread]
    static int Main(string[] args)
    {
        try
        {
            string build = ReadBuildId();
            string baseDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), AppName);
            string appDir = Path.Combine(baseDir, build);
            string exe = Path.Combine(appDir, AppName, AppName + ".exe");

            if (!File.Exists(Path.Combine(appDir, DoneMarker)) || !File.Exists(exe))
            {
                if (!UnpackWithProgress(appDir)) return 1;
                RemoveOldBuilds(baseDir, build);
            }

            var start = new ProcessStartInfo(exe, JoinArguments(args))
            {
                UseShellExecute = false,
                WorkingDirectory = Path.GetDirectoryName(exe)
            };
            Process.Start(start);
            return 0;
        }
        catch (Exception e)
        {
            MessageBox.Show("GanttProject could not be started:\n\n" + e.Message, AppName,
                MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    static string ReadBuildId()
    {
        using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(BuildResource))
        using (var reader = new StreamReader(stream))
        {
            return reader.ReadToEnd().Trim();
        }
    }

    /// Unpacks into a temporary folder first, so that an interrupted first start does not leave a broken copy.
    static bool UnpackWithProgress(string appDir)
    {
        Exception failure = null;
        var form = new Form
        {
            Text = AppName,
            FormBorderStyle = FormBorderStyle.FixedDialog,
            StartPosition = FormStartPosition.CenterScreen,
            ControlBox = false,
            ClientSize = new Size(420, 90),
            AutoScaleMode = AutoScaleMode.Dpi
        };
        form.Controls.Add(new Label
        {
            Text = "Preparing GanttProject for the first start. This takes a moment...",
            Location = new Point(16, 14),
            Size = new Size(390, 24)
        });
        form.Controls.Add(new ProgressBar
        {
            Style = ProgressBarStyle.Marquee,
            Location = new Point(16, 46),
            Size = new Size(388, 22)
        });
        form.Shown += (sender, e) =>
        {
            var worker = new Thread(() =>
            {
                try { Unpack(appDir); }
                catch (Exception ex) { failure = ex; }
                form.BeginInvoke(new Action(form.Close));
            });
            worker.IsBackground = true;
            worker.Start();
        };
        Application.EnableVisualStyles();
        Application.Run(form);
        if (failure != null) throw failure;
        return true;
    }

    static void Unpack(string appDir)
    {
        string tmp = appDir + ".tmp";
        if (Directory.Exists(tmp)) Directory.Delete(tmp, true);
        if (Directory.Exists(appDir)) Directory.Delete(appDir, true);
        Directory.CreateDirectory(tmp);
        using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(PayloadResource))
        using (var zip = new ZipArchive(stream, ZipArchiveMode.Read))
        {
            zip.ExtractToDirectory(tmp);
        }
        File.WriteAllText(Path.Combine(tmp, DoneMarker), DateTime.Now.ToString("o"));
        Directory.Move(tmp, appDir);
    }

    /// Old builds are removed when possible. One that is still running is locked and stays until next time.
    static void RemoveOldBuilds(string baseDir, string currentBuild)
    {
        foreach (var dir in Directory.GetDirectories(baseDir).Where(d => Path.GetFileName(d) != currentBuild))
        {
            try { Directory.Delete(dir, true); } catch (Exception) { }
        }
    }

    static string JoinArguments(string[] args)
    {
        var result = new StringBuilder();
        foreach (var arg in args)
        {
            if (result.Length > 0) result.Append(' ');
            result.Append('"').Append(arg.Replace("\"", "\\\"")).Append('"');
        }
        return result.ToString();
    }
}
