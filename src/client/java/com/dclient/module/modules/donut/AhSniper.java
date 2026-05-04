package com.dclient.module.modules.donut;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class AhSniper extends Module {
    public final Setting<String>  itemName     = addSetting("Item Name",   "diamond sword");
    public final Setting<String>  maxPrice     = addSetting("Max Price",   "1k");
    public final Setting<String>  minPrice     = addSetting("Min Price",   "0");
    public final Setting<String>  priceMode    = addSetting("Price Mode",  "Per Stack", new String[]{"Per Stack","Per Item"});
    public final Setting<Boolean> autoConfirm  = addSetting("Auto Confirm", true);
    public final Setting<Boolean> filterTime   = addSetting("Filter Low Time", true);
    public final Setting<Float>   minHours     = addSetting("Min Hours",   24.0f, 1.0f, 72.0f);
    public final Setting<Boolean> notifs       = addSetting("Notifications", true);
    public final Setting<Boolean> autoSell     = addSetting("Auto Sell",   false);
    public final Setting<String>  sellPrice    = addSetting("Sell Price",  "14m");
    public final Setting<Boolean> webhookOn    = addSetting("Webhook",     false);
    public final Setting<String>  webhookUrl   = addSetting("Webhook URL", "");
    public final Setting<String>  discordId    = addSetting("Discord ID",  "");
    private int navDelay=0,purchaseTimeout=0,invCheck=0,stagnant=0,sellDelay=0;
    private boolean cmdSent=false,buying=false,clickedBuy=false,clickedConfirm=false,refreshed=false,selling=false;
    private int prevCount=0; private String aName=""; private double aPrice=0; private int aQty=0;
    private Item target=null;
    private static final int MAX_STAGNANT=2200,MAX_TIMEOUT=100,MAX_INV=50;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    public AhSniper(){super("AH Sniper",Category.DONUT);}
    @Override
    protected void onEnable() {
        resetState();
        // Resolve item lazily — don't fail silently if registry isn't ready
        try {
            target = resolveItem(itemName.getValue());
        } catch (Exception e) {
            target = null;
        }
        if (target == null || target == Items.AIR) {
            chat("§c[AH Sniper] Unknown item: " + itemName.getValue() + " — check spelling");
            setEnabled(false);
            return;
        }
        if (parsePrice(maxPrice.getValue()) <= 0) {
            chat("§c[AH Sniper] Invalid max price: " + maxPrice.getValue());
            setEnabled(false);
            return;
        }
        if (notifs.getValue())
            chat("§a[AH Sniper] Sniping §f" + target.getName().getString()
                + " §amax §f" + maxPrice.getValue());
        prevCount = countItem();
    }
    @Override protected void onDisable(){resetState();}
    public void tick(){
        if(!isEnabled())return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null)return;
        stagnant++;
        if(stagnant>=MAX_STAGNANT){if(mc.screen!=null)mc.player.closeContainer();resetState();openAH(mc);return;}
        if(navDelay>0){navDelay--;if(navDelay==0)refreshed=false;return;}
        if(sellDelay>0){sellDelay--;if(sellDelay==0)doSell(mc);return;}
        if(buying){
            purchaseTimeout++;
            if(purchaseTimeout>=MAX_TIMEOUT){if(notifs.getValue())chat("§e[AH Sniper] Timed out, retrying...");buying=false;purchaseTimeout=0;invCheck=0;clickedBuy=false;clickedConfirm=false;}
            checkBuy(mc);return;
        }
        if(selling)return;
        AbstractContainerMenu menu=mc.player.containerMenu;
        int cs=menu.slots.size()-36;
        if(cs==27&&isConfirmGUI(menu)){handleConfirm(mc,menu);return;}
        if(cs==54){cmdSent=true;processSix(mc,menu);return;}
        if(cs==27){processThree(mc,menu);return;}
        if(!cmdSent){openAH(mc);cmdSent=true;}
    }
    private void processSix(Minecraft mc,AbstractContainerMenu menu){
        if(refreshed||buying||clickedBuy)return;
        for(int i=0;i<44;i++){
            ItemStack s=menu.getSlot(i).getItem();
            if(!s.is(target)||isShulker(s)||hasCurse(s))continue;
            double p=getPrice(mc,s);
            if(p<0||!isValid(mc,s,p))continue;
            aName=s.getHoverName().getString();aPrice=p;aQty=s.getCount();
            mc.gameMode.handleInventoryMouseClick(menu.containerId,i,1,ClickType.QUICK_MOVE,mc.player);
            clickedBuy=true;buying=true;purchaseTimeout=0;invCheck=0;stagnant=0;
            if(notifs.getValue())chat("§e[AH Sniper] Buying §f"+aQty+"x "+aName+" §efor §f"+fmt(p));
            return;
        }
        mc.gameMode.handleInventoryMouseClick(menu.containerId,49,1,ClickType.QUICK_MOVE,mc.player);
        navDelay=1;refreshed=true;stagnant=0;
    }
    private void processThree(Minecraft mc,AbstractContainerMenu menu){
        if(buying||clickedBuy)return;
        ItemStack s=menu.getSlot(13).getItem();
        if(!s.is(target)||isShulker(s)||hasCurse(s))return;
        double p=getPrice(mc,s);
        if(p<0||!isValid(mc,s,p))return;
        aName=s.getHoverName().getString();aPrice=p;aQty=s.getCount();
        mc.gameMode.handleInventoryMouseClick(menu.containerId,15,1,ClickType.QUICK_MOVE,mc.player);
        clickedBuy=true;buying=true;purchaseTimeout=0;invCheck=0;stagnant=0;
        if(notifs.getValue())chat("§e[AH Sniper] Buying §f"+aQty+"x "+aName+" §efor §f"+fmt(p));
    }
    private void handleConfirm(Minecraft mc,AbstractContainerMenu menu){
        if(!autoConfirm.getValue()||clickedConfirm)return;
        for(int i=0;i<menu.slots.size();i++){
            if(isConfirmBtn(menu.getSlot(i).getItem())){
                mc.gameMode.handleInventoryMouseClick(menu.containerId,i,0,ClickType.PICKUP,mc.player);
                clickedConfirm=true;buying=true;invCheck=0;prevCount=countItem();stagnant=0;
                if(notifs.getValue())chat("§a[AH Sniper] Confirmed!");
                return;
            }
        }
    }
    private void checkBuy(Minecraft mc){
        invCheck++;
        if(invCheck<10)return;
        int cur=countItem();
        if(cur>prevCount){
            int got=cur-prevCount;
            if(notifs.getValue())chat("§a[AH Sniper] Got §f"+got+"x "+aName+" §afor §f"+fmt(aPrice));
            mc.level.playLocalSound(mc.player.getX(),mc.player.getY(),mc.player.getZ(),SoundEvents.PLAYER_LEVELUP,SoundSource.PLAYERS,1f,1f,false);
            sendWebhook(aName,aPrice,got);
            prevCount=cur;buying=false;purchaseTimeout=0;invCheck=0;clickedBuy=false;clickedConfirm=false;stagnant=0;
            if(autoSell.getValue()){selling=true;if(mc.screen!=null)mc.player.closeContainer();sellDelay=2;}
        }else if(invCheck>=MAX_INV){
            if(notifs.getValue())chat("§e[AH Sniper] Purchase may have failed, retrying...");
            buying=false;purchaseTimeout=0;invCheck=0;clickedBuy=false;clickedConfirm=false;
        }
    }
    private void openAH(Minecraft mc){
        if(mc.player==null)return;
        String n=target!=null?getCmd(target):itemName.getValue();
        mc.player.connection.sendCommand("ah "+n);
        navDelay=10;stagnant=0;
    }
    private void doSell(Minecraft mc){
        double p=parsePrice(sellPrice.getValue());
        if(p<=0){selling=false;return;}
        if(mc.player==null){selling=false;return;}
        for(int i=0;i<9;i++){if(mc.player.getInventory().getItem(i).is(target)){mc.player.getInventory().setSelectedSlot(i);break;}}
        mc.player.connection.sendCommand("ah sell "+(int)p);
        selling=false;navDelay=5;stagnant=0;
    }
    private boolean isValid(Minecraft mc,ItemStack s,double price){
        if(filterTime.getValue()){double h=getSelfDestruct(mc,s);if(h>=0&&h<minHours.getValue())return false;}
        double max=parsePrice(maxPrice.getValue()),min=parsePrice(minPrice.getValue());
        if(max<=0)return false;
        double cmp="Per Item".equals(priceMode.getValue())?price/Math.max(1,s.getCount()):price;
        if(min>0&&cmp<min)return false;
        return cmp<=max;
    }
    private double getPrice(Minecraft mc,ItemStack s){
        List<Component> lines=s.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level),mc.player,TooltipFlag.Default.NORMAL);
        return parsePrice(lines);
    }
    private double parsePrice(List<Component> lines){
        Pattern[]ps={Pattern.compile("\\$([\\d,]+(?:\\.\\d+)?)([kmb])?",Pattern.CASE_INSENSITIVE),Pattern.compile("([\\d,]+(?:\\.\\d+)?)([kmb])?\\s*coins?",Pattern.CASE_INSENSITIVE),Pattern.compile("\\b([\\d,]+(?:\\.\\d+)?)([kmb])\\b",Pattern.CASE_INSENSITIVE)};
        for(Component l:lines){String t=l.getString();if(t.toLowerCase().contains("trillion"))return 999_999_999_999_999.0;for(Pattern p:ps){Matcher m=p.matcher(t);if(m.find()){try{double b=Double.parseDouble(m.group(1).replace(",",""));String sf=m.groupCount()>=2&&m.group(2)!=null?m.group(2).toLowerCase():"";double ml=switch(sf){case"k"->1_000.0;case"m"->1_000_000.0;case"b"->1_000_000_000.0;default->1.0;};return b*ml;}catch(NumberFormatException ignored){}}}}
        return -1;
    }
    private double getSelfDestruct(Minecraft mc,ItemStack s){
        List<Component> lines=s.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level),mc.player,TooltipFlag.Default.NORMAL);
        StringBuilder sb=new StringBuilder();for(Component l:lines)sb.append("\n").append(l.getString());
        Matcher m=Pattern.compile("self\\s*destruct[:\\s]*([\\d\\s+d+h+m+s]+)",Pattern.CASE_INSENSITIVE).matcher(sb);
        if(!m.find())return -1;String t=m.group(1).toLowerCase();double h=0;
        Matcher d=Pattern.compile("(\\d+)\\s*d").matcher(t);if(d.find())h+=Double.parseDouble(d.group(1))*24;
        Matcher hr=Pattern.compile("(\\d+)\\s*h").matcher(t);if(hr.find())h+=Double.parseDouble(hr.group(1));
        return h>0?h:-1;
    }
    private boolean isConfirmGUI(AbstractContainerMenu m){for(int i=0;i<Math.min(m.slots.size(),54);i++)if(isConfirmBtn(m.getSlot(i).getItem()))return true;return false;}
    private boolean isConfirmBtn(ItemStack s){if(s.isEmpty())return false;String n=s.getHoverName().getString().toLowerCase();if(n.contains("confirm")||n.contains("buy")||n.contains("yes")||n.contains("accept"))return true;Item i=s.getItem();return i==Items.LIME_WOOL||i==Items.LIME_DYE||i==Items.GREEN_CONCRETE||i==Items.LIME_STAINED_GLASS||i==Items.EMERALD||i==Items.LIME_TERRACOTTA||i==Items.LIME_STAINED_GLASS_PANE;}
    private boolean isShulker(ItemStack s){return s.getItem().getName().getString().toLowerCase().contains("shulker");}
    private boolean hasCurse(ItemStack s){String e=s.getEnchantments().toString().toLowerCase();return e.contains("vanishing_curse")||e.contains("binding_curse");}
    private int countItem(){Minecraft mc=Minecraft.getInstance();if(mc.player==null||target==null)return 0;int c=0;for(int i=0;i<36;i++){ItemStack s=mc.player.getInventory().getItem(i);if(s.is(target))c+=s.getCount();}return c;}
    private void resetState(){navDelay=0;purchaseTimeout=0;invCheck=0;stagnant=0;sellDelay=0;cmdSent=false;buying=false;clickedBuy=false;clickedConfirm=false;refreshed=false;selling=false;aName="";aPrice=0;aQty=0;}
    private void chat(String msg){Minecraft mc=Minecraft.getInstance();mc.execute(()->{if(mc.player!=null)mc.player.displayClientMessage(Component.literal(msg),false);});}
    private double parsePrice(String s){if(s==null||s.isEmpty())return -1;s=s.trim().toLowerCase().replace(",","");double m=1;if(s.endsWith("b")){m=1_000_000_000;s=s.substring(0,s.length()-1);}else if(s.endsWith("m")){m=1_000_000;s=s.substring(0,s.length()-1);}else if(s.endsWith("k")){m=1_000;s=s.substring(0,s.length()-1);}try{return Double.parseDouble(s)*m;}catch(NumberFormatException e){return -1;}}
    private String fmt(double p){if(p>=1_000_000_000)return String.format("%.1fB",p/1_000_000_000);if(p>=1_000_000)return String.format("%.1fM",p/1_000_000);if(p>=1_000)return String.format("%.1fK",p/1_000);return String.format("%.0f",p);}
    public static Item resolveItem(String name){if(name==null||name.isEmpty())return null;String lower=name.trim().toLowerCase();String key=lower.replace(" ","_");for(Item item:BuiltInRegistries.ITEM){var k=BuiltInRegistries.ITEM.getKey(item);if(k!=null&&(k.getPath().equals(key)||k.toString().equals("minecraft:"+key)))return item;}for(Item item:BuiltInRegistries.ITEM){if(item.getName().getString().toLowerCase().equals(lower))return item;}for(Item item:BuiltInRegistries.ITEM){if(item.getName().getString().toLowerCase().contains(lower))return item;}return null;}
    private String getCmd(Item item){var k=BuiltInRegistries.ITEM.getKey(item);return k!=null?k.getPath().replace("_"," "):item.getName().getString().toLowerCase();}
    private void sendWebhook(String name,double price,int qty){if(!webhookOn.getValue()||webhookUrl.getValue().isEmpty())return;String ping=discordId.getValue().isEmpty()?"":"<@"+discordId.getValue()+"> ";String json=String.format("{\"content\":\"%s\",\"username\":\"AH Sniper\",\"embeds\":[{\"title\":\"Item Sniped!\",\"color\":5763719,\"fields\":[{\"name\":\"Item\",\"value\":\"%s x%d\",\"inline\":true},{\"name\":\"Price\",\"value\":\"%s\",\"inline\":true},{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}]}]}",esc(ping+"Sniped "+qty+"x "+name+" for "+fmt(price)),esc(name),qty,esc(fmt(price)),System.currentTimeMillis()/1000);try{HttpRequest req=HttpRequest.newBuilder().uri(URI.create(webhookUrl.getValue())).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json,StandardCharsets.UTF_8)).timeout(Duration.ofSeconds(15)).build();http.sendAsync(req,HttpResponse.BodyHandlers.ofString());}catch(Exception ignored){}}
    private String esc(String s){if(s==null)return"";return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
}
