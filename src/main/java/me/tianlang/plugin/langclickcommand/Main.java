package me.tianlang.plugin.langclickcommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class Main extends JavaPlugin implements Listener{
    @Override
    public void onEnable() {
        getLogger().info("插件已启动");
        //这里开始读取配置文件 
        saveDefaultConfig();
        FileConfiguration c = getConfig();
        for (String s : c.getKeys(false)){
            int amount = c.getBoolean(s+".required.enable") ? c.getInt(s+".required.amount") : 0 ;
            List<String> message = new ArrayList<String>();
            message.add(ChatColor.translateAlternateColorCodes('&', c.getString(s+".message.nocooldown")));
            message.add(ChatColor.translateAlternateColorCodes('&', c.getString(s+".message.noamount")));
            message.add(ChatColor.translateAlternateColorCodes('&', c.getString(s+".message.nolevel")));
            message.add(ChatColor.translateAlternateColorCodes('&', c.getString(s+".message.nopermission")));
            message.add(ChatColor.translateAlternateColorCodes('&', c.getString(s+".message.afteruse")));
            new ClickItem(
                c.getString(s+".type"),
                ChatColor.translateAlternateColorCodes('&', c.getString(s+".lore")),
                c.getStringList(s+".clickways"),
                c.getInt(s+".cooldown"),
                amount,
                c.getInt(s+".needlevel"),
                c.getString(s+".needpermission"),
                message,
                c.getStringList(s+".cmd")
            );
        }

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("插件已卸载");
    }
    @EventHandler
    public void onPlayerClick(final PlayerInteractEvent e){
        //这里检测物品 不要所有物品 都去执行下面的方法 优化插件效率
        final ItemStack item = e.getItem();
        if (item == null || !item.getItemMeta().hasLore())
            return;

        //这里也是优化 先过滤 避免资源消耗 优化插件效率
        List<ClickItem> cis = new ArrayList<ClickItem>(ClickItem.getAllItem());
        //这个是 过滤 满足条件就过滤掉
        cis.removeIf(new Predicate<ClickItem>() {
			public boolean test(ClickItem t) {
                if (
                    t.getType() == item.getType() &&  //判断物品类型
                    t.getClickways().contains(e.getAction()) &&    //判断点击类型
                    item.getItemMeta().getLore().contains(t.getLore())  //判断lore
                ){
                    return false;//true是过滤掉 false是保留
                }
				return true;
			}
        });

        //过滤完后不为空
        if (cis.isEmpty())
            return;

        for (ClickItem ci : cis)
            ci.click(e);

        //不为空就一定要过滤了 就不用了检测返回值了 
        e.setCancelled(true);
    }



}

//创建一个类用来存储物品
class ClickItem{
    private static final List<ClickItem> ALL_ITEM = new ArrayList<ClickItem>();

    //创建字段 属性
    private Material type;
    private String lore;
    private List<Action> clickways = new ArrayList<Action>();
    private int cooldown;
    private int amount;
    private int needlevel;
    private String needpermission;
    private List<String> message;
    private Map<String, rCommand> cmds = new HashMap<String, rCommand>(); //这里用哈希map存储对应关系
    private int maxr;

    public ClickItem(String type,String lore,List<String> clickways,int cooldown,int amount,int needlevel,String needpermission,List<String> message,List<String> cmd){
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
        for (String c : cmd){
            //这里便利 读取这个命令列表
            try {
                int r = Integer.parseInt(StringUtils.substringBefore(c, ":"));
                String cm = c.substring(c.indexOf(":")+1);
                cmds.put(cm, new rCommand(x, x + r));
                x += r;
            } catch (NumberFormatException e) {
                //这个异常是 cs[0] 如果不是数字的情况下 就报错
            }
        }
        this.maxr = x;
        
        ALL_ITEM.add(this);
    }

    public void click(PlayerInteractEvent e){
        Player p = e.getPlayer();
        if (!p.hasPermission(needpermission)){
            p.sendMessage(getMessage(3));   //没有权限的信息
            return;
        }

        if (p.getLevel() < needlevel){
            p.sendMessage(getMessage(2));   //等级不够的信息
            return;
        }

        ItemStack item = e.getItem();
        if (item.getAmount() < amount){
            p.sendMessage(getMessage(1));   //数量不够的信息
            return;
        }

        //从这开始啊 就都是符合要求的 要开始执行命令了
        
        //消耗物品
        item.setAmount(item.getAmount() - amount);
        //执行命令
        p.sendMessage(getMessage(4));   //使用成功的信息
        runCommand(p);
    }

    public void runCommand(Player p){
        double random = Math.random();//这里生成随机数 随机数生成 
        int r = (int) (random * maxr);
        r = r != maxr ? r : 0;  //这个语法之前用到过 叫做三元运算符

        //还是用循环吧 
        for (String s : cmds.keySet()){
            if (cmds.get(s).isRus(r)){
                //执行命令 控制台身份执行 s
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s.replaceAll("%player%", p.getName()));
                return;
            }
        }
    }

    public Material getType(){
        return type;
    }

    public String getLore(){
        return lore;
    }

    public List<Action> getClickways(){
        return clickways;
    }

    public int getCooldown(){
        return cooldown;
    }

    public String getMessage(int msg){
        return message.get(msg);
    }

    public static List<ClickItem> getAllItem(){
        return ALL_ITEM;
    }
}

//数据类型中 没啥能存这个 随机 所以写一个新的类 来封装一下 这个类就这样了 不用管了
class rCommand {
    private int x, y;   //这个用来存储随机范围

    public rCommand(int x, int y){
        this.x = x;
        this.y = y;
    }

    //判断是否在范围内
    public boolean isRus(int x){
        return (x >= this.x && x < y);  //这里可以直接返回表达式 如果大于等于x 同时小于y 就返回true
    }
}