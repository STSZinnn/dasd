package com.sovereignsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/** Artifact IV / VII: Dragonfang, an authentic crossbow with ordinary firing and dragon abilities. */
final class DragonfangArtifact implements Listener {
    static final String TYPE = "dragonfang";
    private static final String VISUAL_DRAGON_TAG = "sovereign_dragon_wrath_visual_v1";
    private final SovereignSMPPlugin plugin;
    private final NamespacedKey artifactKey, tooltipKey, breathBoltKey;
    private UUID token, owner;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> dragonMarks = new HashMap<>();
    // Display-based dragon visuals do not rely on the Ender Dragon boss AI, and
    // consequently survive ordinary Overworld/Nether entity rules until cleanup.
    private final Set<UUID> wrathVisuals = new HashSet<>();

    DragonfangArtifact(SovereignSMPPlugin plugin) {
        this.plugin = plugin;
        artifactKey = new NamespacedKey(plugin, "dragonfang_token");
        tooltipKey = new NamespacedKey(plugin, "dragon");
        breathBoltKey = new NamespacedKey(plugin, "dragon_breath_bolt");
        token = plugin.getArtifactStateUuid("dragonfang.token");
        owner = plugin.getArtifactStateUuid("dragonfang.owner");
        if (token == null) owner = null;
        // Only remove our own visual dragons after a crashed server or a plugin reload.
        cleanupDragons();
    }

    void shutdown(){cleanupDragons();}
    private void cleanupDragons(){
        // Remove the previous releases' stuck Ender Dragons and our cosmetic displays.
        for(World world:Bukkit.getWorlds()) for(Entity entity:world.getEntities()){
            if(entity.getScoreboardTags().contains(VISUAL_DRAGON_TAG)) entity.remove();
        }
        wrathVisuals.clear();
    }

    @EventHandler public void onChunkLoad(ChunkLoadEvent event){
        for(Entity entity:event.getChunk().getEntities()){
            if(entity.getScoreboardTags().contains(VISUAL_DRAGON_TAG)
                    && !wrathVisuals.contains(entity.getUniqueId())) entity.remove();
        }
    }

    private void persist() {
        plugin.setArtifactStateUuid("dragonfang.token", token);
        plugin.setArtifactStateUuid("dragonfang.owner", owner);
        plugin.saveArtifactState();
    }

    boolean minted() { return token != null; }
    String ownerName() {
        if (owner == null) return "Unclaimed";
        String name = Bukkit.getOfflinePlayer(owner).getName();
        return name == null ? owner.toString() : name;
    }
    boolean authentic(ItemStack item) {
        return token != null && item != null && item.getType() == Material.CROSSBOW && item.hasItemMeta()
                && token.toString().equals(item.getItemMeta().getPersistentDataContainer()
                        .get(artifactKey, PersistentDataType.STRING));
    }
    private boolean wielding(Player player) {
        return player.isOnline() && owner != null && owner.equals(player.getUniqueId())
                && authentic(player.getInventory().getItemInMainHand()) && player.getGameMode() != GameMode.SPECTATOR;
    }
    private boolean authorized(Player player) {
        if (owner == null) player.sendMessage(ChatColor.YELLOW + "[Sovereign] Dragonfang is unclaimed. Pick it up or /sovereign claim dragonfang.");
        else if (!owner.equals(player.getUniqueId())) player.sendMessage(ChatColor.RED + "[Sovereign] Only Dragonfang's owner can use it.");
        return owner != null && owner.equals(player.getUniqueId()) && player.getGameMode() != GameMode.SPECTATOR;
    }
    private Component gradient(String input, int left, int right, boolean bold) {
        Component out = Component.empty();
        for (int i = 0; i < input.length(); i++) {
            double p = input.length() <= 1 ? 0 : i / (double) (input.length()-1);
            int r=(int)(((left>>16)&255)*(1-p)+((right>>16)&255)*p);
            int g=(int)(((left>>8)&255)*(1-p)+((right>>8)&255)*p);
            int b=(int)((left&255)*(1-p)+(right&255)*p);
            out=out.append(Component.text(String.valueOf(input.charAt(i)),TextColor.color(r,g,b))
                    .decoration(TextDecoration.BOLD,bold).decoration(TextDecoration.ITALIC,false));
        }
        return out;
    }
    private Component text(String input,int rgb){return Component.text(input,TextColor.color(rgb)).decoration(TextDecoration.ITALIC,false);}
    private void ability(List<Component> lore,String title,String control,String path,int fallback,String a,String b){
        lore.add(Component.empty());
        lore.add(gradient(title,0xDC92FF,0xA15CFE,true));
        lore.add(gradient(control,0xFFCF73,0xAFA9FF,true)
                .append(text("  •  "+plugin.getConfig().getInt(path,fallback)+"s",0xEDD6FE)));
        lore.add(text(a,0xD7C8E9));lore.add(text(b,0xD7C8E9));
    }
    private void applyEnchantments(ItemMeta meta) {
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        // Power is normally bow-only. Unsafe enchant stores Power III on the crossbow;
        // arrow damage is explicitly enhanced in onShoot so the enchant has gameplay value.
        meta.addEnchant(Enchantment.POWER,3,true);
        meta.addEnchant(Enchantment.QUICK_CHARGE,2,true);
        // Explicitly unsafe because vanilla enchanting cannot combine MULTISHOT
        // and PIERCING, nor normally apply INFINITY to a crossbow.
        meta.addEnchant(Enchantment.PIERCING,4,true);
        meta.addEnchant(Enchantment.MULTISHOT,1,true);
        meta.addEnchant(Enchantment.INFINITY,1,true);
        meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
    }
    private void style(ItemMeta meta) {
        meta.displayName(gradient("DRAGONFANG  ✦  THE END",0xDF8BFF,0x7057E8,true));
        List<Component> lore=new ArrayList<>();
        lore.add(gradient("✧  LEGENDARY ARTIFACT  IV / VII  ✧",0xBA77FF,0xE5B5FF,true));
        lore.add(text("Carved from the last echo of the Ender Dragon.",0xB99DCF));
        lore.add(text("Every bolt carries a fragment of the End.",0xB99DCF));
        lore.add(Component.empty());
        lore.add(gradient("PASSIVE  |  ENDER RESONANCE",0xEA91FF,0x9875DB,true));
        lore.add(text("Dragon bolts mark targets with End energy.",0xD7C8E9));
        lore.add(text("Strike a marked foe to trigger a bonus burst.",0xC6ADE4));
        ability(lore,"I  |  DRAGON'S BREATH","RIGHT-CLICK  •  SHOOT A LOADED BOLT",
                "dragon.breath.cooldown-seconds",18,
                "Fire a REAL Ender Dragon fireball instead of", "an arrow; its impact spreads dragon breath.");
        ability(lore,"II  |  CHORUS SHIFT","SNEAK + RIGHT-CLICK",
                "dragon.shift.cooldown-seconds",28,
                "Blink forward to a safe spot in your sight,", "leaving unstable End particles behind.");
        ability(lore,"III  |  DRAGON'S WRATH","F  •  SWAP HANDS",
                "dragon.wrath.cooldown-seconds",60,
                "Summon an animated dragon overhead", "as three breath strikes sweep the ground.");
        lore.add(Component.empty());
        lore.add(gradient("✦  THE END CHOOSES ONLY ONE WIELDER  ✦",0xC78FFF,0x8E84F4,true));
        meta.lore(lore);
        meta.setTooltipStyle(tooltipKey);
    }
    ItemStack restyle(ItemStack input) {
        if(!authentic(input))return input;
        ItemStack out=input.clone();
        ItemMeta meta=out.getItemMeta();
        applyEnchantments(meta);style(meta);out.setItemMeta(meta);
        return out;
    }
    private ItemStack create(){
        ItemStack item=new ItemStack(Material.CROSSBOW);
        ItemMeta meta=item.getItemMeta();
        applyEnchantments(meta);style(meta);
        meta.getPersistentDataContainer().set(artifactKey,PersistentDataType.STRING,token.toString());
        item.setItemMeta(meta);return item;
    }
    void mint(Player recipient,boolean recovery){
        if(minted() && !recovery){recipient.sendMessage(ChatColor.RED+"[Sovereign] Dragonfang already exists. Use recover if lost.");return;}
        token=UUID.randomUUID();owner=recipient.getUniqueId();persist();
        for(ItemStack overflow:recipient.getInventory().addItem(create()).values())
            recipient.getWorld().dropItemNaturally(recipient.getLocation(),overflow);
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE+"[Sovereign] "+recipient.getName()+" now wields Artifact IV / VII — Dragonfang!");
    }
    void claim(Player player){
        if(!minted()){player.sendMessage(ChatColor.RED+"[Sovereign] Dragonfang has not been minted.");return;}
        if(owner!=null){player.sendMessage(ChatColor.RED+"[Sovereign] Dragonfang already has a holder.");return;}
        for(ItemStack item:player.getInventory().getContents())if(authentic(item)){
            owner=player.getUniqueId();persist();
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE+"[Sovereign] "+player.getName()+" claimed Dragonfang (IV / VII)!");return;
        }
        player.sendMessage(ChatColor.RED+"[Sovereign] You must hold the genuine Dragonfang to claim it.");
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPickup(EntityPickupItemEvent event){
        if(!(event.getEntity() instanceof Player picker)||!authentic(event.getItem().getItemStack()))return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!picker.isOnline())return;
            boolean inInventory=false;
            for(int i=0;i<picker.getInventory().getSize();i++){
                ItemStack item=picker.getInventory().getItem(i);
                if(!authentic(item))continue;
                inInventory=true;
                picker.getInventory().setItem(i,restyle(item));
            }
            if(owner==null && inInventory){owner=picker.getUniqueId();persist();
                Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE+"[Sovereign] "+picker.getName()+" claimed Dragonfang (IV / VII)!");}
        });
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event){
        Player victim=event.getEntity();
        if(!victim.getUniqueId().equals(owner))return;
        owner=null;persist();cooldowns.remove(victim.getUniqueId());
        if(event.getKeepInventory()){
            ItemStack drop=null;
            for(int slot=0;slot<victim.getInventory().getSize();slot++){
                ItemStack item=victim.getInventory().getItem(slot);
                if(!authentic(item))continue;
                if(drop==null)drop=item.clone();
                victim.getInventory().setItem(slot,null);
            }
            event.getDrops().removeIf(this::authentic);
            if(drop!=null){ItemStack item=drop;Location at=victim.getLocation().clone();
                Bukkit.getScheduler().runTask(plugin,()->at.getWorld().dropItemNaturally(at,item));}
        }
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE+"[Sovereign] Dragonfang is unclaimed after "+victim.getName()+"'s defeat!");
    }
    private boolean ready(Player player,String id,String path,int fallback,boolean showMessage){
        long now=System.currentTimeMillis();
        Map<String,Long> map=cooldowns.computeIfAbsent(player.getUniqueId(),k->new HashMap<>());
        long end=map.getOrDefault(id,0L);
        if(end>now){if(showMessage)player.sendActionBar(text(id+"  "+String.format(Locale.US,"%.1fs",(end-now)/1000.0),0xE6B6FF));return false;}
        map.put(id,now+1000L*Math.max(0,plugin.getConfig().getInt(path,fallback)));return true;
    }
    Component hud(Player player){
        if(!wielding(player))return null;
        String[] ids={"dragon.breath","dragon.shift","dragon.wrath"},labels={"DB","CS","DW"};
        Component bar=Component.empty();
        for(int i=0;i<ids.length;i++){
            if(i>0)bar=bar.append(text("  │  ",0x87749E));
            long remain=cooldowns.getOrDefault(player.getUniqueId(),Map.of()).getOrDefault(ids[i],0L)-System.currentTimeMillis();
            bar=bar.append(text(labels[i]+" "+(remain<=0?"READY":String.format(Locale.US,"%.1fs",remain/1000.0)),remain<=0?0xD9A4FF:0x8C849B));
        }
        return bar;
    }
    List<String> cooldownLines(Player player){
        List<String> lines=new ArrayList<>();
        for(String id:List.of("dragon.breath","dragon.shift","dragon.wrath")){
            long remaining=cooldowns.getOrDefault(player.getUniqueId(),Map.of()).getOrDefault(id,0L)-System.currentTimeMillis();
            lines.add(ChatColor.GRAY+id+": "+ChatColor.LIGHT_PURPLE+(remaining<=0?"READY":String.format(Locale.US,"%.1fs",remaining/1000.0)));
        }
        return lines;
    }
    // Infinity on a Crossbow is not a vanilla enchantment. The Paper-specific
    // loading hook lets it function without consuming an arrow (an arrow must
    // still be present as normal for the crossbow to begin loading).
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onLoad(EntityLoadCrossbowEvent event){
        if(!(event.getEntity() instanceof Player player)||!authentic(event.getCrossbow()))return;
        if(!authorized(player)){event.setCancelled(true);return;}
        event.setConsumeItem(false);
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onShoot(EntityShootBowEvent event){
        if(!(event.getEntity() instanceof Player player)||!authentic(event.getBow()))return;
        if(!authorized(player)){event.setCancelled(true);return;}
        if(!(event.getProjectile() instanceof AbstractArrow arrow))return;
        // Vanilla crossbows ignore Power; preserve Power III on ordinary bolts.
        arrow.setDamage(arrow.getDamage()+2.0);
        if(!ready(player,"dragon.breath","dragon.breath.cooldown-seconds",18,false))return;
        // Spawn our DragonFireball independently. EntityShootBowEvent#setProjectile
        // may reject an ALREADY SPAWNED fireball (the exception was being shown
        // to the player even though the fireball existed). Leave the vanilla
        // event alone so loading, Multishot, and ammo mechanics complete normally.
        // Remove only the single empowered vanilla bolt next server tick.
        final Vector direction=arrow.getVelocity().lengthSquared()>.01
                ?arrow.getVelocity().clone().normalize():player.getEyeLocation().getDirection().normalize();
        Location muzzle=player.getEyeLocation().clone().add(direction.clone().multiply(1.3));
        try{
            DragonFireball fireball=player.getWorld().spawn(muzzle,DragonFireball.class);
            fireball.setShooter(player);
            fireball.setIsIncendiary(false);
            fireball.setYield(0.0f);
            fireball.setDirection(direction);
            fireball.setVelocity(direction.clone().multiply(1.5));
            fireball.getPersistentDataContainer().set(breathBoltKey,PersistentDataType.STRING,player.getUniqueId().toString());
            Bukkit.getScheduler().runTask(plugin,()->{
                if(arrow.isValid())arrow.remove();
            });
            player.getWorld().playSound(muzzle,Sound.ENTITY_ENDER_DRAGON_SHOOT,1.15f,1.0f);
            player.getWorld().spawnParticle(Particle.DRAGON_BREATH,muzzle,40,.35,.3,.35,.04);
            Bukkit.getScheduler().runTaskLater(plugin,()->{if(fireball.isValid())fireball.remove();},85L);
        }catch(RuntimeException failure){
            // If the world genuinely refuses entity spawning, preserve the normal
            // arrow and refund the cooldown; don't spam chat during PvP.
            Map<String,Long> active=cooldowns.get(player.getUniqueId());
            if(active!=null)active.remove("dragon.breath");
            plugin.getLogger().warning("Dragonfang fireball spawn failed: "+failure.getMessage());
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onProjectileHit(ProjectileHitEvent event){
        Entity projectile=event.getEntity();
        if(!(projectile instanceof DragonFireball) && !(projectile instanceof AbstractArrow))return;
        String shooterId=projectile.getPersistentDataContainer().get(breathBoltKey,PersistentDataType.STRING);
        if(shooterId==null)return;
        projectile.getPersistentDataContainer().remove(breathBoltKey);
        if(!(event.getEntity().getShooter() instanceof Player shooter)||!shooter.getUniqueId().toString().equals(shooterId))return;
        if(!shooter.getUniqueId().equals(owner))return;
        Location impact=projectile.getLocation().clone();
        makeBreathCloud(shooter,impact);
        // Do not leave a vanilla dragon fireball producing additional untracked damage.
        if(projectile instanceof DragonFireball)projectile.remove();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void noFireballGrief(EntityExplodeEvent event){
        if(event.getEntity() instanceof DragonFireball fireball && fireball.getPersistentDataContainer().has(breathBoltKey,PersistentDataType.STRING)){
            event.blockList().clear();event.setYield(0f);
        }
        if(event.getEntity() instanceof EnderDragon dragon && dragon.getScoreboardTags().contains(VISUAL_DRAGON_TAG)){
            event.blockList().clear();event.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void noDragonGrief(EntityChangeBlockEvent event){
        if(event.getEntity() instanceof EnderDragon dragon && dragon.getScoreboardTags().contains(VISUAL_DRAGON_TAG))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void noVisualDragonContactDamage(EntityDamageByEntityEvent event){
        if(event.getDamager() instanceof EnderDragon dragon && dragon.getScoreboardTags().contains(VISUAL_DRAGON_TAG))event.setCancelled(true);
        if(event.getDamager() instanceof org.bukkit.entity.ComplexEntityPart part
                && part.getParent() instanceof EnderDragon dragon
                && dragon.getScoreboardTags().contains(VISUAL_DRAGON_TAG))event.setCancelled(true);
    }
    private List<LivingEntity> nearby(Player caster,Location center,double radius){
        List<LivingEntity> result=new ArrayList<>();
        for(Entity entity:center.getWorld().getNearbyEntities(center,radius,radius,radius))
            if(plugin.usableTarget(caster,entity)&&entity.getLocation().distanceSquared(center)<=radius*radius)result.add((LivingEntity)entity);
        return result;
    }
    private void makeBreathCloud(Player shooter,Location center){
        World world=center.getWorld();
        int duration=Math.max(20,Math.min(120,plugin.getConfig().getInt("dragon.breath.cloud-duration-ticks",60)));
        double radius=plugin.getConfig().getDouble("dragon.breath.radius-blocks",2.7);
        double amount=plugin.getConfig().getDouble("dragon.breath.damage-per-pulse-health-points",1.0);
        world.playSound(center,Sound.ENTITY_ENDER_DRAGON_GROWL,.7f,1.5f);
        new BukkitRunnable(){int t=0;final java.util.Set<UUID> cloudSeen=new java.util.HashSet<>();
            @Override public void run(){
                if(t>=duration){cancel();return;}
                world.spawnParticle(Particle.DRAGON_BREATH,center,35,radius*.5,.35,radius*.5,.015);
                world.spawnParticle(Particle.PORTAL,center,10,radius*.45,.35,radius*.45,.05);
                if(t%20==0 && shooter.isOnline() && shooter.getWorld().equals(world)){
                    for(LivingEntity target:nearby(shooter,center,radius)){
                        plugin.dealSovereignAbilityDamage(target,shooter,amount);
                        Long previous=dragonMarks.get(target.getUniqueId());
                        if(cloudSeen.add(target.getUniqueId())
                                && previous!=null && previous>System.currentTimeMillis()){
                            plugin.dealSovereignAbilityDamage(target,shooter,plugin.getConfig().getDouble("dragon.passive.mark-burst-damage-health-points",1.0));
                            world.spawnParticle(Particle.REVERSE_PORTAL,target.getLocation().add(0,.9,0),22,.4,.4,.4,.1);
                        }
                        dragonMarks.put(target.getUniqueId(),System.currentTimeMillis()+5000L);
                    }
                }
                t+=5;
            }
        }.runTaskTimer(plugin,0L,5L);
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event){
        if(event.getHand()!=EquipmentSlot.HAND)return;
        if(event.getAction()!=Action.RIGHT_CLICK_AIR&&event.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        if(!authentic(event.getItem()))return;
        if(!event.getPlayer().isSneaking())return; // normal right-click MUST load/fire the crossbow
        event.setCancelled(true);
        if(authorized(event.getPlayer()))chorusShift(event.getPlayer());
    }
    private void chorusShift(Player player){
        if(!ready(player,"dragon.shift","dragon.shift.cooldown-seconds",28,true))return;
        Location eye=player.getEyeLocation();Vector dir=eye.getDirection().normalize();
        double maxDist=Math.max(3,Math.min(20,plugin.getConfig().getDouble("dragon.shift.range-blocks",8)));
        Location destination=null;
        for(double d=1.0;d<=maxDist;d+=.5){
            Location sample=eye.clone().add(dir.clone().multiply(d));
            Location feet=sample.getBlock().getLocation().add(.5,0,.5);
            if(!feet.getBlock().isPassable()||!feet.clone().add(0,1,0).getBlock().isPassable())break;
            if(feet.clone().add(0,-1,0).getBlock().getType().isSolid() && !feet.getBlock().isLiquid())
                destination=feet;
        }
        if(destination==null){
            Map<String,Long> playerCooldowns=cooldowns.get(player.getUniqueId());
            if(playerCooldowns!=null)playerCooldowns.remove("dragon.shift");
            player.sendMessage(ChatColor.GRAY+"[Sovereign] No safe Chorus Shift landing spot in sight.");return;
        }
        Location old=player.getLocation().clone();destination.setDirection(old.getDirection());
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL,old.clone().add(0,1,0),70,.5,.8,.5,.15);
        player.teleport(destination);
        player.getWorld().spawnParticle(Particle.DRAGON_BREATH,destination.clone().add(0,1,0),80,.6,.8,.6,.06);
        player.getWorld().playSound(destination,Sound.ENTITY_ENDERMAN_TELEPORT,1.0f,.8f);
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent event){
        if(!authentic(event.getPlayer().getInventory().getItemInMainHand()))return;
        event.setCancelled(true);
        if(authorized(event.getPlayer()))dragonsWrath(event.getPlayer());
    }
    /** A small, animated dragon silhouette built from vanilla display entities.
     *  Unlike an EnderDragon mob, these are not managed by the End fight/phase AI.
     *  Every wing/body part is temporary, harmless, and tagged for crash cleanup.
     */
    private record DragonPart(Entity entity,double forward,double side,double elevation,
                              boolean wing,int wingSide,double wingExtension){}

    private BlockDisplay dragonBlock(World world,Location at,Material material,
                                    float width,float height,float depth,float yaw){
        BlockDisplay display=world.spawn(at,BlockDisplay.class,part->{
            part.setBlock(material.createBlockData());
            part.setPersistent(false);
            part.setDisplayWidth(16f);
            part.setDisplayHeight(12f);
            part.setViewRange(3.0f);
            part.setShadowRadius(0f);
            part.setTeleportDuration(2);
            part.setInterpolationDuration(2);
            part.setTransformation(new Transformation(
                    new Vector3f(-width/2f,-height/2f,-depth/2f),
                    new Quaternionf().rotateY(yaw),
                    new Vector3f(width,height,depth),new Quaternionf()));
            part.addScoreboardTag(VISUAL_DRAGON_TAG);
        });
        wrathVisuals.add(display.getUniqueId());
        return display;
    }

    private ItemDisplay dragonHead(World world,Location at,float yaw){
        ItemDisplay display=world.spawn(at,ItemDisplay.class,part->{
            part.setItemStack(new ItemStack(Material.DRAGON_HEAD));
            part.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            part.setPersistent(false);
            part.setDisplayWidth(16f);
            part.setDisplayHeight(12f);
            part.setViewRange(3.0f);
            part.setShadowRadius(0f);
            part.setTeleportDuration(2);
            part.setInterpolationDuration(2);
            part.setTransformation(new Transformation(
                    new Vector3f(0,0,0),new Quaternionf().rotateY(yaw),
                    new Vector3f(2.5f,2.5f,2.5f),new Quaternionf()));
            part.addScoreboardTag(VISUAL_DRAGON_TAG);
        });
        wrathVisuals.add(display.getUniqueId());
        return display;
    }

    private Location dragonOffset(Location center,Vector forward,Vector sideVector,
                                  double front,double lateral,double y){
        return center.clone().add(forward.clone().multiply(front))
                .add(sideVector.clone().multiply(lateral)).add(0,y,0);
    }

    private List<DragonPart> createVisualDragon(World world,Location starting,Vector forward){
        List<DragonPart> parts=new ArrayList<>();
        Vector right=new Vector(-forward.getZ(),0,forward.getX());
        float yaw=(float)Math.atan2(forward.getX(),forward.getZ());
        try{
            // Body, neck, dragon-head item and segmented tail.
            parts.add(new DragonPart(dragonBlock(world,dragonOffset(starting,forward,right,0,0,0),Material.BLACK_CONCRETE,2.4f,1.45f,3.2f,yaw),0,0,0,false,0,0));
            parts.add(new DragonPart(dragonBlock(world,dragonOffset(starting,forward,right,2,0,.05),Material.GRAY_CONCRETE,1.15f,.85f,2.0f,yaw),2,0,.05,false,0,0));
            parts.add(new DragonPart(dragonHead(world,dragonOffset(starting,forward,right,3.35,0,-.15),yaw),3.35,0,-.15,false,0,0));
            for(int i=0;i<5;i++){
                double front=-2-i*1.12, vertical=-.15-i*.18;
                parts.add(new DragonPart(dragonBlock(world,dragonOffset(starting,forward,right,front,0,vertical),
                        i<3?Material.BLACK_CONCRETE:Material.GRAY_CONCRETE,
                        (float)(1.05-i*.13),.7f,(float)(1.75-i*.12),yaw),front,0,vertical,false,0,0));
            }
            // Each wing has four animated membrane panels and three bones.
            for(int wingSide:new int[]{-1,1}){
                for(int seg=0;seg<4;seg++){
                    double extent=1.7+seg*1.37, front=-.3+seg*.25, lateral=wingSide*extent;
                    parts.add(new DragonPart(dragonBlock(world,dragonOffset(starting,forward,right,front,lateral,.4),
                            Material.BLACK_STAINED_GLASS,2.45f,.13f,(float)(3.1-seg*.25),yaw),
                            front,lateral,.4,true,wingSide,extent));
                    if(seg<3){
                        front=.3+seg*.22;lateral=wingSide*(extent+.25);
                        parts.add(new DragonPart(dragonBlock(world,dragonOffset(starting,forward,right,front,lateral,.56),
                                Material.GRAY_CONCRETE,1.7f,.19f,.22f,yaw),
                                front,lateral,.56,true,wingSide,extent));
                    }
                }
            }
            for(int wingSide:new int[]{-1,1}){
                double lateral=wingSide*.75;
                parts.add(new DragonPart(dragonBlock(world,dragonOffset(starting,forward,right,-1.1,lateral,-1),
                        Material.GRAY_CONCRETE,.4f,1.3f,.5f,yaw),-1.1,lateral,-1,false,0,0));
            }
            return parts;
        }catch(RuntimeException failure){
            removeWrathParts(parts);
            throw failure;
        }
    }

    private void positionVisualDragon(List<DragonPart> parts,Location center,
                                      Vector forward,Vector sideVector,int age){
        double flap=Math.sin(age*.20);
        for(DragonPart part:parts){
            if(!part.entity().isValid())continue;
            Location at=center.clone().add(forward.clone().multiply(part.forward()))
                    .add(sideVector.clone().multiply(part.side()))
                    .add(0,part.elevation()+(part.wing()?flap*(.35+part.wingExtension()*.18):0),0);
            part.entity().teleport(at);
        }
    }

    private void removeWrathParts(List<DragonPart> parts){
        for(DragonPart part:parts){
            wrathVisuals.remove(part.entity().getUniqueId());
            if(part.entity().isValid())part.entity().remove();
        }
    }

    private void dragonsWrath(Player player){
        if(!ready(player,"dragon.wrath","dragon.wrath.cooldown-seconds",60,true))return;
        final Location origin=player.getLocation().clone();
        Vector dir=player.getEyeLocation().getDirection().setY(0);
        if(dir.lengthSquared()<.01)dir=new Vector(0,0,1);
        final Vector forward=dir.normalize();
        final Vector side=new Vector(-forward.getZ(),0,forward.getX());
        final World world=origin.getWorld();
        world.playSound(origin,Sound.ENTITY_ENDER_DRAGON_GROWL,1.6f,.85f);
        player.sendActionBar(gradient("DRAGON'S WRATH  ✦  THE END ANSWERS",0xB760FF,0xF0B5FF,true));
        final Location starting=origin.clone().add(forward.clone().multiply(5)).add(0,12,0);
        if(starting.getY()>world.getMaxHeight()-5)starting.setY(world.getMaxHeight()-5);
        final List<DragonPart> parts=new ArrayList<>();
        try{
            parts.addAll(createVisualDragon(world,starting,forward));
        }catch(RuntimeException failure){
            removeWrathParts(parts);
            plugin.getLogger().warning("Dragon display could not be spawned: "+failure.getMessage());
            scriptedBreathOnly(player,origin,forward);
            return;
        }
        // Hard cleanup always runs, even if the player logs off during the attack.
        Bukkit.getScheduler().runTaskLater(plugin,()->removeWrathParts(parts),110L);
        new BukkitRunnable(){int age=0;
            @Override public void run(){
                if(age>=80 || !player.isOnline() || !world.equals(player.getWorld())){
                    removeWrathParts(parts);cancel();return;
                }
                try{
                    Location flight=origin.clone().add(forward.clone().multiply(5+age*.28))
                            .add(side.clone().multiply(Math.sin(age*.075)*2.7))
                            .add(0,12+Math.sin(age*.09)*1.5,0);
                    flight.setY(Math.min(world.getMaxHeight()-5,Math.max(world.getMinHeight()+5,flight.getY())));
                    positionVisualDragon(parts,flight,forward,side,age);
                    world.spawnParticle(Particle.DRAGON_BREATH,flight,16,1.8,.6,1.8,.018);
                    if(age%20==0)world.playSound(flight,Sound.ENTITY_ENDER_DRAGON_FLAP,1.2f,.85f);
                    if(age==24||age==44||age==64){
                        int phase=age==24?0:age==44?1:2;
                        dragonBreathImpact(player,origin,forward,phase);
                    }
                    age+=2;
                }catch(RuntimeException failure){
                    plugin.getLogger().warning("Dragon's Wrath display interrupted: "+failure.getMessage());
                    removeWrathParts(parts);cancel();
                }
            }
        }.runTaskTimer(plugin,0L,2L);
    }

    private void dragonBreathImpact(Player player,Location origin,Vector forward,int phase){
        World world=origin.getWorld();
        Location impact=origin.clone().add(forward.clone().multiply(4.5+phase*3.5));
        world.playSound(impact,Sound.ENTITY_ENDER_DRAGON_SHOOT,1.0f,1.1f-phase*.14f);
        world.spawnParticle(Particle.DRAGON_BREATH,impact.clone().add(0,1.2,0),95,1.9,1.3,1.9,.07);
        world.spawnParticle(Particle.EXPLOSION,impact.clone().add(0,1,0),4,1.2,.6,1.2,.03);
        for(LivingEntity target:nearby(player,impact,3.5)){
            plugin.dealSovereignAbilityDamage(target,player,
                    plugin.getConfig().getDouble("dragon.wrath.strike-damage-health-points",4.0));
            target.setVelocity(target.getVelocity().add(new Vector(0,.3,0)));
        }
    }
    private void scriptedBreathOnly(Player player,Location origin,Vector forward){
        for(int phase=0;phase<3;phase++){
            final int step=phase;
            Bukkit.getScheduler().runTaskLater(plugin,()->{
                if(player.isOnline()&&origin.getWorld().equals(player.getWorld()))
                    dragonBreathImpact(player,origin,forward,step);
            },24L+phase*20L);
        }
    }
}
