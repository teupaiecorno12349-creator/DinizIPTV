using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Hosting;

namespace DinizIPTV.Admin;

public sealed class MainForm : Form
{
    readonly Store store = new();
    readonly BindingSource accountsSource = new();
    readonly BindingSource contentSource = new();
    WebApplication? app;
    CancellationTokenSource? discoveryCts;
    readonly Label serverStatus = new(){AutoSize=true,ForeColor=Color.LightGreen,Text="Servidor iniciando..."};
    readonly DataGridView accounts = Grid();
    readonly DataGridView content = Grid();

    public MainForm()
    {
        Text="Diniz IPTV Admin PC"; Width=1250; Height=760; MinimumSize=new Size(950,650); BackColor=Color.FromArgb(11,14,20); ForeColor=Color.White; Font=new Font("Segoe UI",10);
        var tabs=new TabControl{Dock=DockStyle.Fill};
        tabs.TabPages.Add(ServerPage()); tabs.TabPages.Add(AccountsPage()); tabs.TabPages.Add(ContentPage()); Controls.Add(tabs);
        accountsSource.DataSource=store.Data.Accounts;accounts.DataSource=accountsSource;
        contentSource.DataSource=store.Data.Content;content.DataSource=contentSource;
        Shown+=async(_,_)=>await StartServer(); FormClosing+=async(_,_)=>{discoveryCts?.Cancel();if(app!=null)await app.StopAsync();};
    }

    static DataGridView Grid()=>new(){Dock=DockStyle.Fill,AutoGenerateColumns=true,ReadOnly=true,SelectionMode=DataGridViewSelectionMode.FullRowSelect,MultiSelect=false,BackgroundColor=Color.FromArgb(20,24,32),ForeColor=Color.Black,AutoSizeColumnsMode=DataGridViewAutoSizeColumnsMode.Fill};
    Button Btn(string s)=>new(){Text=s,AutoSize=true,Height=34,FlatStyle=FlatStyle.Flat,ForeColor=Color.White,BackColor=Color.FromArgb(93,63,211)};
    TextBox Box(string hint)=>new(){PlaceholderText=hint,Width=180};
    FlowLayoutPanel Bar()=>new(){Dock=DockStyle.Top,Height=54,Padding=new Padding(10),BackColor=Color.FromArgb(20,24,32),WrapContents=false};

    TabPage ServerPage(){var p=new TabPage("Servidor"){BackColor=Color.FromArgb(11,14,20),ForeColor=Color.White};var box=new FlowLayoutPanel{Dock=DockStyle.Fill,FlowDirection=FlowDirection.TopDown,Padding=new Padding(24),BackColor=p.BackColor};var title=new Label{Text="DINIZ IPTV — ADMIN PC",AutoSize=true,Font=new Font("Segoe UI",22,FontStyle.Bold),ForeColor=Color.White};box.Controls.Add(title);box.Controls.Add(new Label{Text="Servidor LAN na porta 8787. Player e Admin devem estar na mesma rede.",AutoSize=true,ForeColor=Color.Silver});box.Controls.Add(serverStatus);var restart=Btn("Reiniciar servidor");box.Controls.Add(restart);restart.Click+=async(_,_)=>{if(app!=null)await app.StopAsync();await StartServer();};p.Controls.Add(box);return p;}

    TabPage AccountsPage(){var p=new TabPage("Contas"){BackColor=Color.FromArgb(11,14,20)};var bar=Bar();var u=Box("Usuário");var pw=Box("Senha");pw.PasswordChar='●';var exp=new DateTimePicker{Width=140,Format=DateTimePickerFormat.Short,Value=DateTime.Today.AddMonths(1)};var add=Btn("Criar conta"),renew=Btn("Renovar +30 dias"),del=Btn("Excluir");bar.Controls.AddRange([u,pw,exp,add,renew,del]);add.Click+=(_,_)=>{if(u.Text.Trim().Length<1||pw.Text.Length<4){MessageBox.Show("Informe usuário e senha com pelo menos 4 caracteres.");return;}try{store.AddAccount(u.Text.Trim(),pw.Text,exp.Value.Date.AddDays(1).AddSeconds(-1));RefreshAll();u.Clear();pw.Clear();}catch(Exception ex){MessageBox.Show(ex.Message);}};renew.Click+=(_,_)=>{if(accounts.CurrentRow?.DataBoundItem is Account a){a.ExpiresAt=Math.Max(a.ExpiresAt,DateTimeOffset.Now.ToUnixTimeMilliseconds())+(long)TimeSpan.FromDays(30).TotalMilliseconds;store.Save();RefreshAll();}};del.Click+=(_,_)=>{if(accounts.CurrentRow?.DataBoundItem is Account a){store.Data.Accounts.Remove(a);store.Save();RefreshAll();}};p.Controls.Add(accounts);p.Controls.Add(bar);return p;}

    TabPage ContentPage(){var p=new TabPage("Catálogo"){BackColor=Color.FromArgb(11,14,20)};var bar=new FlowLayoutPanel{Dock=DockStyle.Top,Height=100,Padding=new Padding(10),BackColor=Color.FromArgb(20,24,32),WrapContents=true};var type=new ComboBox{Width=90,DropDownStyle=ComboBoxStyle.DropDownList};type.Items.AddRange(["movie","series"]);type.SelectedIndex=0;var title=Box("Título");var year=Box("Ano");year.Width=70;var url=Box("URL MP4/HLS/DASH/OK.ru/TeraBox");url.Width=310;var poster=Box("URL da capa");poster.Width=240;var genres=Box("Gêneros");var season=new NumericUpDown{Width=65,Maximum=999};var episode=new NumericUpDown{Width=65,Maximum=999};var add=Btn("Adicionar"),del=Btn("Excluir");bar.Controls.AddRange([type,title,year,url,poster,genres,new Label{Text="T",ForeColor=Color.White,AutoSize=true,Padding=new Padding(0,8,0,0)},season,new Label{Text="E",ForeColor=Color.White,AutoSize=true,Padding=new Padding(0,8,0,0)},episode,add,del]);add.Click+=(_,_)=>{if(title.Text.Trim()==""||!Uri.TryCreate(url.Text.Trim(),UriKind.Absolute,out _)){MessageBox.Show("Título e URL HTTP/HTTPS válidos são obrigatórios.");return;}store.AddContent(new ContentItem{Type=type.Text,Title=title.Text.Trim(),Year=year.Text.Trim(),StreamUrl=url.Text.Trim(),PosterUrl=poster.Text.Trim(),Genres=genres.Text.Trim(),Season=(int)season.Value,Episode=(int)episode.Value});RefreshAll();title.Clear();year.Clear();url.Clear();poster.Clear();genres.Clear();season.Value=episode.Value=0;};del.Click+=(_,_)=>{if(content.CurrentRow?.DataBoundItem is ContentItem c){store.Data.Content.Remove(c);store.Save();RefreshAll();}};p.Controls.Add(content);p.Controls.Add(bar);return p;}

    void RefreshAll(){accountsSource.ResetBindings(false);contentSource.ResetBindings(false);}

    async Task StartServer()
    {
        try
        {
            var builder=WebApplication.CreateBuilder();builder.WebHost.UseUrls("http://0.0.0.0:8787");app=builder.Build();
            app.MapGet("/api/health",()=>Results.Json(new{ok=true,name="Diniz IPTV Admin PC",version="1.0"}));
            app.MapPost("/api/login",async(HttpContext ctx)=>{var req=await JsonSerializer.DeserializeAsync<LoginRequest>(ctx.Request.Body,new JsonSerializerOptions{PropertyNameCaseInsensitive=true});if(req==null)return Results.BadRequest();var a=store.Auth(req.Username??"",req.Password??"");if(a==null)return Results.Unauthorized();var token=Convert.ToHexString(RandomNumberGenerator.GetBytes(24));store.Tokens[token]=DateTime.UtcNow.AddHours(12);return Results.Json(new{token});});
            app.MapGet("/api/catalog",(string token)=>store.ValidToken(token)?Results.Json(store.Data.Content):Results.Unauthorized());
            await app.StartAsync();serverStatus.Text=$"● Servidor ativo — http://{LocalIp()}:8787";StartDiscovery();
        }
        catch(Exception ex){serverStatus.Text="Falha no servidor: "+ex.Message;serverStatus.ForeColor=Color.LightCoral;}
    }

    void StartDiscovery()
    {
        discoveryCts?.Cancel();discoveryCts=new CancellationTokenSource();var ct=discoveryCts.Token;
        _=Task.Run(async()=>{using var udp=new UdpClient(8788);while(!ct.IsCancellationRequested){try{var r=await udp.ReceiveAsync(ct);var s=Encoding.UTF8.GetString(r.Buffer);if(s=="DINIZ_IPTV_DISCOVER"){var msg=Encoding.UTF8.GetBytes($"DINIZ_IPTV_ADMIN|http://{LocalIp()}:8787");await udp.SendAsync(msg,msg.Length,r.RemoteEndPoint);}}catch(OperationCanceledException){break;}catch{await Task.Delay(250,ct);}}},ct);
    }

    static string LocalIp(){foreach(var ni in NetworkInterface.GetAllNetworkInterfaces()){if(ni.OperationalStatus!=OperationalStatus.Up)continue;foreach(var ua in ni.GetIPProperties().UnicastAddresses){if(ua.Address.AddressFamily==AddressFamily.InterNetwork&&!IPAddress.IsLoopback(ua.Address))return ua.Address.ToString();}}return "127.0.0.1";}
}

public sealed class Store
{
    readonly string file=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"DinizIPTV","admin-data.json");
    public StoreData Data {get;private set;}=new(); public Dictionary<string,DateTime> Tokens {get;}=new();
    public Store(){Directory.CreateDirectory(Path.GetDirectoryName(file)!);try{if(File.Exists(file))Data=JsonSerializer.Deserialize<StoreData>(File.ReadAllText(file))??new();}catch{Data=new();}}
    public void Save()=>File.WriteAllText(file,JsonSerializer.Serialize(Data,new JsonSerializerOptions{WriteIndented=true}));
    public void AddAccount(string user,string pass,DateTime expires){if(Data.Accounts.Any(x=>x.Username.Equals(user,StringComparison.OrdinalIgnoreCase)))throw new Exception("Usuário já existe.");var salt=RandomNumberGenerator.GetBytes(16);var hash=Rfc2898DeriveBytes.Pbkdf2(pass,salt,120000,HashAlgorithmName.SHA256,32);Data.Accounts.Add(new Account{Id=NextAccount(),Username=user,Salt=Convert.ToBase64String(salt),PasswordHash=Convert.ToBase64String(hash),ExpiresAt=new DateTimeOffset(expires).ToUnixTimeMilliseconds()});Save();}
    public Account? Auth(string user,string pass){var a=Data.Accounts.FirstOrDefault(x=>x.Username.Equals(user,StringComparison.OrdinalIgnoreCase));if(a==null||a.ExpiresAt<DateTimeOffset.Now.ToUnixTimeMilliseconds())return null;try{var got=Rfc2898DeriveBytes.Pbkdf2(pass,Convert.FromBase64String(a.Salt),120000,HashAlgorithmName.SHA256,32);return CryptographicOperations.FixedTimeEquals(got,Convert.FromBase64String(a.PasswordHash))?a:null;}catch{return null;}}
    public bool ValidToken(string t){if(Tokens.TryGetValue(t,out var until)&&until>DateTime.UtcNow)return true;Tokens.Remove(t);return false;}
    public void AddContent(ContentItem c){c.Id=Data.Content.Count==0?1:Data.Content.Max(x=>x.Id)+1;Data.Content.Add(c);Save();}
    long NextAccount()=>Data.Accounts.Count==0?1:Data.Accounts.Max(x=>x.Id)+1;
}
public sealed class StoreData{public List<Account> Accounts{get;set;}=new();public List<ContentItem> Content{get;set;}=new();}
public sealed class Account{public long Id{get;set;}public string Username{get;set;}="";public string Salt{get;set;}="";public string PasswordHash{get;set;}="";public long ExpiresAt{get;set;}public string Vencimento=>DateTimeOffset.FromUnixTimeMilliseconds(ExpiresAt).LocalDateTime.ToString("dd/MM/yyyy");}
public sealed class ContentItem{public long Id{get;set;}public string Type{get;set;}="movie";public string Title{get;set;}="";public string EpisodeTitle{get;set;}="";public string Year{get;set;}="";public string Description{get;set;}="";public string PosterUrl{get;set;}="";public string Genres{get;set;}="";public string StreamUrl{get;set;}="";public int Season{get;set;}public int Episode{get;set;}public string DisplayTitle=>Type=="series"&&Episode>0?$"{Title} T{Season}E{Episode}":Title;}
public sealed class LoginRequest{public string? Username{get;set;}public string? Password{get;set;}}
