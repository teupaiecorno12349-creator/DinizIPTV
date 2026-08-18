using LibVLCSharp.Shared;

namespace DinizIPTV.Player;

internal static class Program
{
    [STAThread]
    static void Main()
    {
        Core.Initialize();
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm());
    }
}
