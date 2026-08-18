using System.Net;
using System.Net.Http.Json;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using LibVLCSharp.Shared;
using LibVLCSharp.WinForms;
using Microsoft.Web.WebView2.WinForms;

namespace DinizIPTV.Player;

public sealed class MainForm : Form
{
    readonly HttpClient http = new() { Timeout = TimeSpan.FromSeconds(15) };
    readonly TextBox server = new() { Text = "http://127.0.0.1:8787", Width = 260 };
    readonly TextBox user = new() { PlaceholderText = "Usuário", Width = 150 };
    readonly TextBox pass = new() { PlaceholderText = "Senha", PasswordChar = '●', Width = 150 };
    readonly TextBox search = new() { PlaceholderText = "Pesquisar filmes e séries...", Width = 260 };
    readonly FlowLayoutPanel cards = new() { Dock = DockStyle.Fill, AutoScroll = true, Padding = new Padding(18), BackColor = Color.FromArgb(11,14,20) };
    readonly Label status = new() { AutoSize = true, ForeColor = Color.Silver, Text = "Desconectado" };
    string token = "";
    List<ContentItem> all = new();

    public MainForm()
    {
        Text = "Diniz IPTV Player PC";
        Width = 1280; Height = 760; MinimumSize = new Size(900,600);
        BackColor = Color.FromArgb(11,14,20); ForeColor = Color.White;
        Font = new Font("Segoe UI", 10);

        var top = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 54, Padding = new Padding(10,10,10,4), BackColor = Color.FromArgb(20,24,32), WrapContents = false };
        var find = Btn("Encontrar Admin"); var login = Btn("Entrar");
        top.Controls.AddRange([new Label{Text="Servidor",AutoSize=true,Padding=new Padding(0,7,4,0),ForeColor=Color.White},server,find,user,pass,login,status]);
        Controls.Add(cards); Controls.Add(top);

        var searchBar = new Panel{Dock=DockStyle.Top,Height=50,Padding=new Padding(18,8,18,4),BackColor=Color.FromArgb(11,14,20)};
        search.Dock = DockStyle.Left; searchBar.Controls.Add(search); Controls.Add(searchBar); searchBar.BringToFront(); top.BringToFront();
        search.TextChanged += (_,_) => Render();
        find.Click += async (_,_) => await Discover();
        login.Click += async (_,_) => await Login();
        FormClosed += (_,_) => http.Dispose();
    }

    Button Btn(string s) => new(){Text=s,AutoSize=true,Height=30,FlatStyle=FlatStyle.Flat,ForeColor=Color.White,BackColor=Color.FromArgb(93,63,211)};

    async Task Discover()
    {
        status.Text = "Procurando Admin na rede...";
        try
        {
            using var udp = new UdpClient(8789); udp.EnableBroadcast = true;
            var request = Encoding.UTF8.GetBytes("DINIZ_IPTV_DISCOVER");
            await udp.SendAsync(request, request.Length, new IPEndPoint(IPAddress.Broadcast, 8788));
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(3));
            var r = await udp.ReceiveAsync(cts.Token);
            var text = Encoding.UTF8.GetString(r.Buffer);
            if(text.StartsWith("DINIZ_IPTV_ADMIN|")) { server.Text = text.Split('|')[1].TrimEnd('/'); status.Text="Admin encontrado"; }
            else status.Text="Resposta inválida";
        }
        catch { status.Text = "Admin não encontrado; informe o IP manualmente."; }
    }

    async Task Login()
    {
        try
        {
            status.Text="Conectando...";
            var baseUrl=server.Text.Trim().TrimEnd('/');
            var res=await http.PostAsJsonAsync(baseUrl+"/api/login",new { username=user.Text.Trim(), password=pass.Text });
            if(!res.IsSuccessStatusCode){status.Text="Usuário/senha inválidos ou conta vencida";return;}
            var json=await res.Content.ReadFromJsonAsync<JsonElement>(); token=json.GetProperty("token").GetString()??"";
            all=await http.GetFromJsonAsync<List<ContentItem>>(baseUrl+"/api/catalog?token="+Uri.EscapeDataString(token))??new();
            status.Text=$"Conectado • {all.Count} itens"; Render();
        }
        catch(Exception ex){status.Text="Falha: "+ex.Message;}
    }

    void Render()
    {
        cards.SuspendLayout(); cards.Controls.Clear();
        string q=search.Text.Trim();
        foreach(var item in all.Where(x=>string.IsNullOrEmpty(q)||x.Title.Contains(q,StringComparison.OrdinalIgnoreCase)||x.Genres.Contains(q,StringComparison.OrdinalIgnoreCase)))
        {
            var p=new Panel{Width=190,Height=285,Margin=new Padding(9),BackColor=Color.FromArgb(24,29,39),Cursor=Cursors.Hand};
            var pic=new PictureBox{Dock=DockStyle.Top,Height=210,SizeMode=PictureBoxSizeMode.Zoom,BackColor=Color.FromArgb(35,41,52)};
            if(Uri.TryCreate(item.PosterUrl,UriKind.Absolute,out var poster)) pic.LoadAsync(poster.ToString());
            var name=new Label{Dock=DockStyle.Fill,Text=item.DisplayTitle,ForeColor=Color.White,Font=new Font("Segoe UI",10,FontStyle.Bold),Padding=new Padding(8),AutoEllipsis=true};
            p.Controls.Add(name);p.Controls.Add(pic);
            void open(object? s,EventArgs e)=>Open(item); p.Click+=open;pic.Click+=open;name.Click+=open;
            cards.Controls.Add(p);
        }
        cards.ResumeLayout();
    }

    void Open(ContentItem item)
    {
        if(IsWeb(item.StreamUrl)) new WebPlayerForm(item.DisplayTitle,item.StreamUrl).ShowDialog(this);
        else new VlcPlayerForm(item.DisplayTitle,item.StreamUrl).ShowDialog(this);
    }

    static bool IsWeb(string url)
    {
        if(!Uri.TryCreate(url,UriKind.Absolute,out var u)) return false;
        var h=u.Host.ToLowerInvariant();
        return h=="ok.ru"||h.EndsWith(".ok.ru")||h.Contains("terabox")||h.Contains("1024tera")||h.Contains("terashare")||h.Contains("terafileshare");
    }
}

public sealed class VlcPlayerForm : Form
{
    LibVLC lib; MediaPlayer player; VideoView video;
    public VlcPlayerForm(string title,string url)
    {
        Text=title;WindowState=FormWindowState.Maximized;FormBorderStyle=FormBorderStyle.None;BackColor=Color.Black;KeyPreview=true;
        lib=new LibVLC("--network-caching=1500","--file-caching=1000");player=new MediaPlayer(lib);video=new VideoView{Dock=DockStyle.Fill,MediaPlayer=player};Controls.Add(video);
        Shown+=(_,_)=>{using var m=new Media(lib,new Uri(url));player.Play(m);};
        KeyDown+=(_,e)=>{if(e.KeyCode==Keys.Escape)Close();if(e.KeyCode==Keys.Space)player.SetPause(player.IsPlaying);};
        FormClosed+=(_,_)=>{player.Stop();player.Dispose();lib.Dispose();};
    }
}

public sealed class WebPlayerForm : Form
{
    readonly WebView2 web=new(){Dock=DockStyle.Fill};
    public WebPlayerForm(string title,string url)
    {
        Text=title;WindowState=FormWindowState.Maximized;FormBorderStyle=FormBorderStyle.None;BackColor=Color.Black;KeyPreview=true;Controls.Add(web);
        Shown+=async(_,_)=>{await web.EnsureCoreWebView2Async();web.CoreWebView2.Settings.AreDevToolsEnabled=false;web.CoreWebView2.Settings.IsStatusBarEnabled=false;web.Source=Normalize(url);};
        KeyDown+=(_,e)=>{if(e.KeyCode==Keys.Escape)Close();};
    }
    static Uri Normalize(string url)
    {
        if(Uri.TryCreate(url,UriKind.Absolute,out var u)&&u.Host.EndsWith("ok.ru",StringComparison.OrdinalIgnoreCase))
        {
            var seg=u.AbsolutePath.Split('/',StringSplitOptions.RemoveEmptyEntries);
            if(seg.Length>=2&&(seg[0].Equals("video",StringComparison.OrdinalIgnoreCase)||seg[0].Equals("videoembed",StringComparison.OrdinalIgnoreCase))&&long.TryParse(seg[1],out _))
                return new Uri("https://ok.ru/videoembed/"+seg[1]+"?autoplay=1&nochat=1");
        }
        return new Uri(url);
    }
}

public sealed class ContentItem
{
    public long Id {get;set;} public string Type {get;set;}="movie"; public string Title {get;set;}=""; public string EpisodeTitle {get;set;}="";
    public string Year {get;set;}=""; public string Description {get;set;}=""; public string PosterUrl {get;set;}=""; public string Genres {get;set;}=""; public string StreamUrl {get;set;}="";
    public int Season {get;set;} public int Episode {get;set;}
    public string DisplayTitle => Type=="series"&&Episode>0?$"{Title} • T{Season}E{Episode} {EpisodeTitle}".Trim():string.IsNullOrWhiteSpace(Year)?Title:$"{Title} ({Year})";
}
