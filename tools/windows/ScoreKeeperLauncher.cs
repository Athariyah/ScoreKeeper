using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Windows.Forms;

internal static class ScoreKeeperLauncher
{
    [STAThread]
    private static void Main()
    {
        string root = AppDomain.CurrentDomain.BaseDirectory;
        string backendPath = Path.Combine(root, "BackendRelease", "ScoreKeeperAppBackend.exe");
        string frontendPath = Path.Combine(root, "FrontendRelease", "ScoreKeeper", "ScoreKeeper.exe");
        Process backend = null;

        try
        {
            if (!File.Exists(backendPath) || !File.Exists(frontendPath))
                throw new FileNotFoundException("Не найден backend или frontend. Проверьте структуру папки релиза.");

            int port = FindPort();
            var backendInfo = new ProcessStartInfo
            {
                FileName = backendPath,
                Arguments = "--port " + port,
                WorkingDirectory = Path.GetDirectoryName(backendPath),
                UseShellExecute = false,
                CreateNoWindow = true
            };
            backend = Process.Start(backendInfo);
            if (backend == null) throw new Exception("Не удалось запустить backend.");

            bool ready = false;
            for (int i = 0; i < 300; i++)
            {
                if (backend.HasExited)
                    throw new Exception("Backend завершился при запуске. Возможно, выбранный порт уже занят.");
                if (PortIsOpen(port))
                {
                    ready = true;
                    break;
                }
                Thread.Sleep(100);
            }
            if (!ready) throw new Exception("Backend не запустился за 30 секунд.");

            var frontendInfo = new ProcessStartInfo
            {
                FileName = frontendPath,
                WorkingDirectory = Path.GetDirectoryName(frontendPath),
                UseShellExecute = false
            };
            frontendInfo.EnvironmentVariables["SCOREKEEPER_PORT"] = port.ToString();
            using (Process frontend = Process.Start(frontendInfo))
            {
                if (frontend == null) throw new Exception("Не удалось запустить frontend.");
                frontend.WaitForExit();
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show(ex.Message, "ScoreKeeper", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
        finally
        {
            if (backend != null)
            {
                try
                {
                    if (!backend.HasExited)
                    {
                        using (Process killer = Process.Start(new ProcessStartInfo
                        {
                            FileName = "taskkill.exe",
                            Arguments = "/PID " + backend.Id + " /T /F",
                            UseShellExecute = false,
                            CreateNoWindow = true
                        }))
                        {
                            if (killer != null) killer.WaitForExit();
                        }
                    }
                }
                catch { }
                backend.Dispose();
            }
        }
    }

    private static int FindPort()
    {
        for (int port = 8000; port <= 65535; port++)
        {
            TcpListener listener = null;
            try
            {
                listener = new TcpListener(IPAddress.Loopback, port);
                listener.ExclusiveAddressUse = true;
                listener.Start();
                return port;
            }
            catch (SocketException) { }
            finally { if (listener != null) listener.Stop(); }
        }
        throw new Exception("Не удалось найти свободный порт.");
    }

    private static bool PortIsOpen(int port)
    {
        try
        {
            using (var client = new TcpClient())
            {
                client.Connect(IPAddress.Loopback, port);
                return true;
            }
        }
        catch (SocketException) { return false; }
    }
}
