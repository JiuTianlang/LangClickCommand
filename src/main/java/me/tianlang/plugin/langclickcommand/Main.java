package me.tianlang.plugin.langclickcommand;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class Main extends JavaPlugin implements Listener {

    // 启动插件
    @Override
    public void onEnable() {
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("lcc").setExecutor(this);
    }

    // 加载配置文件
    public void loadConfig() {
        // 创建配置文件
        File f = new File(getDataFolder(), "item.yml");
        if (!f.exists())
            saveResource("item.yml", false);

        // 读取配置文件
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);

        for (String s : c.getKeys(false)) {
            int amount = c.getBoolean(s + ".required.enable") ? c.getInt(s + ".required.amount") : 0;
            List<String> message = new ArrayList<String>();
            message.add(ChatColor.translateAlternateColorCodes('&', c.getString(s + ".message.nocooldown")));
            message.add(ChatColor.translateAlternateColorCodes('&', c.getString(s + ".message.noamount")));
            message.add(ChatColor.translateAlternateColorCodes('&', c.getString(s + ".message.nolevel")));
            message.add(ChatColor.translateAlternateColorCodes('&', c.getString(s + ".message.nopermission")));
            message.add(ChatColor.translateAlternateColorCodes('&', c.getString(s + ".message.afteruse")));
            new ClickItem(
                    s,
                    c.getString(s + ".type"),
                    ChatColor.translateAlternateColorCodes('&', c.getString(s + ".lore")),
                    c.getStringList(s + ".clickways"),
                    c.getInt(s + ".cooldown"), 
                    amount, 
                    c.getInt(s + ".needlevel"),
                    c.getString(s + ".needpermission"), 
                    message, 
                    c.getStringList(s + ".cmd"));
        }
    }

    // 玩家左键右键的事件
    @EventHandler
    public void onPlayerClick(final PlayerInteractEvent e) {
        // 这里检测物品 不要所有物品 都去执行下面的方法 优化插件效率
        final ItemStack item = e.getItem();
        if (item == null || !item.getItemMeta().hasLore())
            return;

        // 这里也是优化 先过滤 避免资源消耗 优化插件效率
        List<ClickItem> cis = new ArrayList<ClickItem>(ClickItem.getAllItem());
        // 这个是 过滤 满足条件就过滤掉
        cis.removeIf(new Predicate<ClickItem>() {
            public boolean test(ClickItem t) {
                if (t.getType() == item.getType() && // 判断物品类型
                t.getClickways().contains(e.getAction()) && // 判断点击类型
                item.getItemMeta().getLore().contains(t.getLore()) // 判断lore
                ) {
                    return false;// true是过滤掉 false是保留
                }
                return true;
            }
        });

        // 过滤完后不为空
        if (cis.isEmpty())
            return;

        for (ClickItem ci : cis)
            ci.click(e);

        // 不为空就一定要过滤了 就不用了检测返回值了
        e.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("lcc.reload")) {
                ClickItem.getAllItem().clear();
                loadConfig();
                sender.sendMessage("重载成功");

            } else {
                sender.sendMessage("你没有使用这条命令的权限");
            }
            return true;
        }
        command.setUsage("/lcc reload 重载插件");
        return false;
    }
}

// 创建一个类用来存储物品
class ClickItem {
    private static final List<ClickItem> ALL_ITEM = new ArrayList<ClickItem>();

    // 创建字段 属性
    private String unique;
    private Material type;
    private String lore;
    private List<Action> clickways = new ArrayList<Action>();
    private int cooldown;
    private int amount;
    private int needlevel;
    private String needpermission;
    private List<String> message;
    private List<rCommand> cmds = new ArrayList<rCommand>(); // 这里用list存储命令列表
    private int maxr;

    public ClickItem(String unique,String type, String lore, List<String> clickways, int cooldown, int amount, int needlevel,
            String needpermission, List<String> message, List<String> cmd) {
        this.unique = unique;
        this.type = Material.getMaterial(type);
        this.lore = lore;
        this.cooldown = cooldown;
        this.amount = amount;
        this.needlevel = needlevel;
        this.needpermission = needpermission;
        this.message = message;
        for (String s : clickways)
            this.clickways.add(Action.valueOf(s.toUpperCase()));

        int x = 0;
        for (String c : cmd) {
            // 这里便利 读取这个命令列表
            try {
                String[] cm = c.split(":");
                int r = Integer.parseInt(cm[0]);
                List<String> cs = new ArrayList<String>();
                for (int i = 1; i < cm.length; i++) {
                    cs.add(cm[i].replaceAll("%冒号%", ":"));
                }
                cmds.add(new rCommand(cs, x, x + r));
                x += r;
            } catch (NumberFormatException e) {
                // 这个异常是 cs[0] 如果不是数字的情况下 就报错
            }
        }
        this.maxr = x;

        ALL_ITEM.add(this);
    }

    public void click(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission(needpermission)) {
            if (!getMessage(3).equals(""))
            p.sendMessage(getMessage(3)); // 没有权限的信息
            return;
        }

        if (p.getLevel() < needlevel) {
            if (!getMessage(2).equals(""))
            p.sendMessage(getMessage(2)); // 等级不够的信息
            return;
        }

        ItemStack item = e.getItem();
        if (item.getAmount() < amount) {
            if (!getMessage(1).equals(""))
            p.sendMessage(getMessage(1)); // 数量不够的信息
            return;
        }

        if (cooldown > 0){
            int time = Cooldown.getCooldown(unique, p);
            if (time > 0){
                if (!getMessage(0).equals(""))
                p.sendMessage(getMessage(0).replaceAll("%time%", String.valueOf(time)));
                return;
            }else{
                new Cooldown(unique, p, cooldown);
            }
        }

        // 从这开始啊 就都是符合要求的 要开始执行命令了

        // 消耗物品
        item.setAmount(item.getAmount() - amount);
        // 执行命令
        if (!getMessage(4).equals(""))
        p.sendMessage(getMessage(4)); // 使用成功的信息
        runCommand(p);
    }

    public void runCommand(Player p) {
        double random = Math.random();// 这里生成随机数 随机数生成
        int r = (int) (random * maxr);
        r = r != maxr ? r : 0; // 这个语法之前用到过 叫做三元运算符

        // 还是用循环吧
        for (rCommand cmd : cmds) {
            if (cmd.isRus(r)) {
                // 执行命令 控制台身份执行 s
                cmd.runCommand(p);
                return;
            }
        }
    }

    public Material getType() {
        return type;
    }

    public String getLore() {
        return lore;
    }

    public List<Action> getClickways() {
        return clickways;
    }

    public int getCooldown() {
        return cooldown;
    }

    public String getMessage(int msg) {
        return message.get(msg);
    }

    public static List<ClickItem> getAllItem() {
        return ALL_ITEM;
    }
}

// 数据类型中 没啥能存这个 随机 所以写一个新的类 来封装一下 这个类就这样了 不用管了
class rCommand {
    private List<String> commands;
    private int x, y; // 这个用来存储随机范围

    public rCommand(List<String> commands, int x, int y) {
        this.commands = commands;
        this.x = x;
        this.y = y;
    }

    public void runCommand(Player p) {
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replaceAll("%player%", p.getName()));
        }
    }

    // 判断是否在范围内
    public boolean isRus(int x) {
        return (x >= this.x && x < y); // 这里可以直接返回表达式 如果大于等于x 同时小于y 就返回true
    }
}

// 这边存冷却时间
class Cooldown {
    private static Map<String, Cooldown> cooldowns = new HashMap<>();

    private String unique;
    private UUID puid;
    private long time;

    public Cooldown(String unique, Player p, int time) {
        this.puid = p.getUniqueId();
        this.time = System.currentTimeMillis() + (time * 50);
        cooldowns.put(puid + unique, this);
    }

    public void remove(){
        cooldowns.remove(puid + unique);
    }

    public static int getCooldown(String unique, Player p){
        Cooldown s = cooldowns.get(p.getUniqueId() + unique);
        int i = 0;
        if (s != null){
            long it = s.time - System.currentTimeMillis();
            i = it > 0 ? (int)(it/1000) : 0;
            if (i == 0)
            s.remove();
        }
        return i;
    }
}