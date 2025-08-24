package org.dantesys.reliquiasNexus;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.dantesys.reliquiasNexus.eventos.*;
import org.dantesys.reliquiasNexus.items.ItemsRegistro;
import org.dantesys.reliquiasNexus.items.Nexus;
import org.dantesys.reliquiasNexus.util.NexusKeys;
import org.dantesys.reliquiasNexus.util.Troca;
import org.dantesys.reliquiasNexus.util.UpdaterCheck;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.dantesys.reliquiasNexus.util.NexusKeys.*;

public final class ReliquiasNexus extends JavaPlugin implements Listener {
    private static final Map<UUID, Troca> trocasPendentes = new HashMap<>();
    private static final Map<UUID, Emprestimo> emprestimos = new HashMap<>();
    private static final Map<String, Banco> bancos = new HashMap<>();
    private static final Map<UUID, Missao> missoesAtivas = new HashMap<>();
    private static final Map<UUID, Loja> lojasAbertas = new HashMap<>();
    private static FileConfiguration config;
    private static YamlConfiguration lang;
    private static final List<String> names = List.of("guerreiro","ceifador","vida","mares","barbaro",
            "fazendeiro","espiao","arqueiro","cacador","tempestade","mineiro","fenix","protetor",
            "hulk","sculk","pescador","flash","mago","ladrao","domador");

    // Classes internas
    public static class Emprestimo {
        private final UUID jogador;
        private final String banco;
        private final double valor;
        private final double valorTotal;
        private final long dataContracao;
        private int diasAtraso;

        public Emprestimo(UUID jogador, String banco, double valor, double juros) {
            this.jogador = jogador;
            this.banco = banco;
            this.valor = valor;
            this.valorTotal = valor * (1 + juros);
            this.dataContracao = System.currentTimeMillis();
            this.diasAtraso = 0;
        }

        public UUID getJogador() { return jogador; }
        public String getBanco() { return banco; }
        public double getValor() { return valor; }
        public double getValorTotal() { return valorTotal; }
        public long getDataContracao() { return dataContracao; }
        public int getDiasAtraso() { return diasAtraso; }
        public void setDiasAtraso(int dias) { this.diasAtraso = dias; }

        public double getValorDevido() {
            long diasPassados = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - dataContracao);
            if (diasPassados > 3) {
                int diasAtraso = (int) (diasPassados - 3);
                double multa = valorTotal * (0.05 * diasAtraso);
                return valorTotal + multa;
            }
            return valorTotal;
        }
    }

    public static class Banco {
        private final String nome;
        private final UUID dono;
        private double saldo;
        private double maxEmprestimo;
        private double taxaJuros;
        private double taxaSucesso;
        private String descricao;
        private boolean aprovado;

        public Banco(String nome, UUID dono, double saldoInicial) {
            this.nome = nome;
            this.dono = dono;
            this.saldo = saldoInicial;
            this.maxEmprestimo = 1000;
            this.taxaJuros = 0.2;
            this.taxaSucesso = 0.7;
            this.descricao = "Novo banco";
            this.aprovado = false;
        }

        public String getNome() { return nome; }
        public UUID getDono() { return dono; }
        public double getSaldo() { return saldo; }
        public void setSaldo(double saldo) { this.saldo = saldo; }
        public double getMaxEmprestimo() { return maxEmprestimo; }
        public void setMaxEmprestimo(double max) { this.maxEmprestimo = max; }
        public double getTaxaJuros() { return taxaJuros; }
        public void setTaxaJuros(double taxa) { this.taxaJuros = taxa; }
        public double getTaxaSucesso() { return taxaSucesso; }
        public void setTaxaSucesso(double taxa) { this.taxaSucesso = taxa; }
        public String getDescricao() { return descricao; }
        public void setDescricao(String desc) { this.descricao = desc; }
        public boolean isAprovado() { return aprovado; }
        public void setAprovado(boolean aprovado) { this.aprovado = aprovado; }
    }

    public static class Missao {
        private final String nome;
        private final String descricao;
        private final double recompensa;
        private final Material icone;
        private final List<String> objetivos;
        private final long tempoExpiracao;

        public Missao(String nome, String descricao, double recompensa, Material icone, List<String> objetivos, int minutosDuracao) {
            this.nome = nome;
            this.descricao = descricao;
            this.recompensa = recompensa;
            this.icone = icone;
            this.objetivos = objetivos;
            this.tempoExpiracao = System.currentTimeMillis() + (minutosDuracao * 60000L);
        }

        public String getNome() { return nome; }
        public String getDescricao() { return descricao; }
        public double getRecompensa() { return recompensa; }
        public Material getIcone() { return icone; }
        public List<String> getObjetivos() { return objetivos; }
        public long getTempoExpiracao() { return tempoExpiracao; }
        public boolean isExpirada() { return System.currentTimeMillis() > tempoExpiracao; }
    }

    public static class Loja {
        private final Inventory inventario;
        private final Map<Integer, ItemLoja> itens;

        public Loja() {
            this.inventario = Bukkit.createInventory(null, 54, Component.text("§6🏪 Loja de Relíquias"));
            this.itens = new HashMap<>();
            inicializarItens();
        }

        private void inicializarItens() {
            // Item informativo
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta infoMeta = info.getItemMeta();
            infoMeta.displayName(Component.text("§e🏪 Sistema de Loja"));
            infoMeta.lore(Arrays.asList(
                    Component.text("§a✅ Sistema ativo!"),
                    Component.text("§7Compre e venda relíquias"),
                    Component.text("§7usando Moly como moeda"),
                    Component.text(""),
                    Component.text("§e💵 Preços dinâmicos baseados na economia"),
                    Component.text("§e📈 Oferta e demanda afetam os valores"),
                    Component.text(""),
                    Component.text("§a🛒 Clique em um item para comprar")
            ));
            info.setItemMeta(infoMeta);
            inventario.setItem(4, info);

            // Adicionar relíquias à venda
            int slot = 9;
            for (String reliquiaNome : names) {
                if (slot >= 44) break; // Limitar ao espaço disponível

                Nexus nexus = ItemsRegistro.getFromNome(reliquiaNome);
                if (nexus != null) {
                    double preco = calcularPreco(reliquiaNome);
                    ItemStack item = nexus.getItem(1);
                    ItemMeta meta = item.getItemMeta();

                    List<Component> lore = new ArrayList<>();
                    if (meta.hasLore()) {
                        lore.addAll(meta.lore());
                    }
                    lore.add(Component.text(""));
                    lore.add(Component.text("§6💰 Preço: §e" + preco + " Moly"));
                    lore.add(Component.text("§a🛒 Clique para comprar"));

                    meta.lore(lore);
                    meta.displayName(Component.text("§b" + reliquiaNome.substring(0, 1).toUpperCase() + reliquiaNome.substring(1)));
                    item.setItemMeta(meta);

                    inventario.setItem(slot, item);
                    itens.put(slot, new ItemLoja(reliquiaNome, preco, 1));

                    slot++;
                    if (slot % 9 == 8) slot += 2; // Pular linha
                }
            }

            // Item de fechar
            ItemStack fechar = new ItemStack(Material.BARRIER);
            ItemMeta fecharMeta = fechar.getItemMeta();
            fecharMeta.displayName(Component.text("§c✖ Fechar Loja"));
            fechar.setItemMeta(fecharMeta);
            inventario.setItem(49, fechar);
        }

        private double calcularPreco(String reliquiaNome) {
            // Preço base + variação aleatória
            double base = 1000;
            double variacao = new Random().nextDouble() * 500;
            return Math.round(base + variacao);
        }

        public Inventory getInventario() { return inventario; }
        public Map<Integer, ItemLoja> getItens() { return itens; }
    }

    public static class ItemLoja {
        private final String reliquia;
        private final double preco;
        private final int nivel;

        public ItemLoja(String reliquia, double preco, int nivel) {
            this.reliquia = reliquia;
            this.preco = preco;
            this.nivel = nivel;
        }

        public String getReliquia() { return reliquia; }
        public double getPreco() { return preco; }
        public int getNivel() { return nivel; }
    }

    @Override
    public void onEnable() {
        ItemsRegistro.init();
        saveResource("lang/pt-br.yml", true);
        saveResource("lang/en-us.yml", true);
        saveDefaultConfig();
        config = getConfig();
        String tipo = config.getString("lang");
        if (tipo == null) {
            tipo = "en-us";
            config.set("lang", "en-us");
        }
        File file = new File(this.getDataFolder(), "/lang/" + tipo + ".yml");
        lang = YamlConfiguration.loadConfiguration(file);
        saveConfig();
        try {
            lang.save(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        new UpdaterCheck(this, "dantesys/nexus-plugin").checkForUpdates();

        // Carregar dados
        carregarDadosEconomicos();
        carregarMissoes();

        // Comando principal para jogadores
        LiteralArgumentBuilder<CommandSourceStack> playerRoot = Commands.literal("nexus").executes(ctx -> {
            CommandSender sender = ctx.getSource().getSender();

            if (sender instanceof Player) {
                if (sender.isOp()) {
                    // Menu para operadores
                    sender.sendMessage("§a✨ === §6Comandos Nexus §a(OP)§a ===");
                    sender.sendMessage(Component.text("§b📖 /nexus livro")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para obter o livro de relíquias")))
                            .clickEvent(ClickEvent.runCommand("/nexus livro")));
                    sender.sendMessage(Component.text("§b⚡ /nexus evoluir")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para evoluir sua relíquia")))
                            .clickEvent(ClickEvent.runCommand("/nexus evoluir")));
                    sender.sendMessage(Component.text("§b🎯 /nexus missoes")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para abrir menu de missões")))
                            .clickEvent(ClickEvent.runCommand("/nexus missoes")));
                    sender.sendMessage(Component.text("§b🏪 /nexus loja")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para abrir a loja de relíquias")))
                            .clickEvent(ClickEvent.runCommand("/nexus loja")));
                    sender.sendMessage(Component.text("§b📋 /nexus list")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para listar todas as relíquias")))
                            .clickEvent(ClickEvent.runCommand("/nexus list")));
                    sender.sendMessage(Component.text("§b📊 /nexus level")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para ver seus níveis")))
                            .clickEvent(ClickEvent.runCommand("/nexus level")));
                    sender.sendMessage(Component.text("§b💰 /nexus carteira")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para ver sua carteira")))
                            .clickEvent(ClickEvent.runCommand("/nexus carteira")));
                    sender.sendMessage(Component.text("§b💳 /nexus emprestimo")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para acessar sistema de empréstimos")))
                            .clickEvent(ClickEvent.runCommand("/nexus emprestimo")));
                    sender.sendMessage(Component.text("§b🏦 /nexus banco")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para acessar sistema bancário")))
                            .clickEvent(ClickEvent.runCommand("/nexus banco")));
                    sender.sendMessage(Component.text("§b🔄 /nexus trocar <jogador>")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para trocar relíquia com outro jogador")))
                            .clickEvent(ClickEvent.suggestCommand("/nexus trocar ")));
                    sender.sendMessage(Component.text("§b✅ /nexus aceitar")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para aceitar uma troca pendente")))
                            .clickEvent(ClickEvent.runCommand("/nexus aceitar")));
                    sender.sendMessage(Component.text("§b❌ /nexus cancelar")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para cancelar uma troca pendente")))
                            .clickEvent(ClickEvent.runCommand("/nexus cancelar")));
                    sender.sendMessage(Component.text("§b🌐 /nexus servidor-economia")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para ver informações do Banco Central")))
                            .clickEvent(ClickEvent.runCommand("/nexus servidor-economia")));
                    sender.sendMessage(Component.text("§b⚙️ /nexusadmin")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para ver comandos administrativos")))
                            .clickEvent(ClickEvent.runCommand("/nexusadmin")));
                } else {
                    // Menu para jogadores normais
                    sender.sendMessage("§a✨ === §6Comandos Nexus §a===");
                    sender.sendMessage(Component.text("§b📖 /nexus livro")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para obter o livro de relíquias 📚")))
                            .clickEvent(ClickEvent.runCommand("/nexus livro")));
                    sender.sendMessage(Component.text("§b⚡ /nexus evoluir")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para evoluir sua relíquia ⚡")))
                            .clickEvent(ClickEvent.runCommand("/nexus evoluir")));
                    sender.sendMessage(Component.text("§b🎯 /nexus missoes")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para abrir menu de missões 🎯")))
                            .clickEvent(ClickEvent.runCommand("/nexus missoes")));
                    sender.sendMessage(Component.text("§b🏪 /nexus loja")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para abrir la loja de relíquias 🏪")))
                            .clickEvent(ClickEvent.runCommand("/nexus loja")));
                    sender.sendMessage(Component.text("§b📋 /nexus list")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para listar todas as relíquias 📋")))
                            .clickEvent(ClickEvent.runCommand("/nexus list")));
                    sender.sendMessage(Component.text("§b📊 /nexus level")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para ver seus níveis 📊")))
                            .clickEvent(ClickEvent.runCommand("/nexus level")));
                    sender.sendMessage(Component.text("§b💰 /nexus carteira")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para ver sua carteira 💰")))
                            .clickEvent(ClickEvent.runCommand("/nexus carteira")));
                    sender.sendMessage(Component.text("§b💳 /nexus emprestimo")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para acessar sistema de empréstimos 💳")))
                            .clickEvent(ClickEvent.runCommand("/nexus emprestimo")));
                    sender.sendMessage(Component.text("§b🏦 /nexus banco")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para acessar sistema bancário 🏦")))
                            .clickEvent(ClickEvent.runCommand("/nexus banco")));
                    sender.sendMessage(Component.text("§b🔄 /nexus trocar <jogador>")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para trocar relíquia com outro jogador 🔄")))
                            .clickEvent(ClickEvent.suggestCommand("/nexus trocar ")));
                    sender.sendMessage(Component.text("§b✅ /nexus aceitar")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para aceitar uma troca pendente ✅")))
                            .clickEvent(ClickEvent.runCommand("/nexus aceitar")));
                    sender.sendMessage(Component.text("§b❌ /nexus cancelar")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para cancelar uma troca pendente ❌")))
                            .clickEvent(ClickEvent.runCommand("/nexus cancelar")));
                    sender.sendMessage(Component.text("§b🌐 /nexus servidor-economia")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para ver informações do Banco Central 🌐")))
                            .clickEvent(ClickEvent.runCommand("/nexus servidor-economia")));
                }
            } else {
                // Menu para console
                sender.sendMessage("§a✨ === Comandos Nexus ===");
                sender.sendMessage("§b📖 /nexus livro - Obter livro de relíquias");
                sender.sendMessage("§b⚡ /nexus evoluir - Evoluir relíquia");
                sender.sendMessage("§b🎯 /nexus missoes - Abrir menu de missões");
                sender.sendMessage("§b🏪 /nexus loja - Abrir loja de relíquias");
                sender.sendMessage("§b📋 /nexus list - Listar relíquias");
                sender.sendMessage("§b📊 /nexus level - Ver níveis");
                sender.sendMessage("§b🗑️ /nexus remover <reliquia> <jogador> - Remover relíquia (OP)");
                sender.sendMessage("§b💰 /nexus carteira - Ver sua carteira");
                sender.sendMessage("§b💳 /nexus emprestimo - Sistema de empréstimos");
                sender.sendMessage("§b🏦 /nexus banco - Sistema bancário");
                sender.sendMessage("§b🔄 /nexus trocar <jogador> - Trocar relíquia");
                sender.sendMessage("§b✅ /nexus aceitar - Aceitar troca");
                sender.sendMessage("§b❌ /nexus cancelar - Cancelar troca");
                sender.sendMessage("§b🌐 /nexus servidor-economia - Info do Banco Central");
            }
            return Command.SINGLE_SUCCESS;
        });

        // Adicionar todos os subcomandos para jogadores
        playerRoot.then(createLivroCommand());
        playerRoot.then(createEvoluirCommand());
        playerRoot.then(createMissoesCommand());
        playerRoot.then(createLojaCommand());
        playerRoot.then(createListCommand());
        playerRoot.then(createLevelCommand());
        playerRoot.then(createCarteiraCommand());
        playerRoot.then(createEmprestimoCommand());
        playerRoot.then(createBancoCommand());
        playerRoot.then(createTrocarCommand());
        playerRoot.then(createAceitarCommand());
        playerRoot.then(createCancelarCommand());
        playerRoot.then(createServidorEconomiaCommand());

        // Comandos apenas para OP
        LiteralArgumentBuilder<CommandSourceStack> adminRoot = Commands.literal("nexusadmin")
                .requires(sender -> sender.getSender().isOp())
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    sender.sendMessage("§4⚙️ === Comandos Administrativos Nexus ===");
                    sender.sendMessage(Component.text("§c🗑️ /nexusadmin remover <reliquia> <jogador>")
                            .hoverEvent(HoverEvent.showText(Component.text("Remover uma relíquia de um jogador")))
                            .clickEvent(ClickEvent.suggestCommand("/nexusadmin remover ")));
                    sender.sendMessage(Component.text("§c🎁 /nexusadmin dar <jogador> <reliquia>")
                            .hoverEvent(HoverEvent.showText(Component.text("Dar uma relíquia específica a um jogador")))
                            .clickEvent(ClickEvent.suggestCommand("/nexusadmin dar ")));
                    sender.sendMessage(Component.text("§c🎲 /nexusadmin receber <jogadores>")
                            .hoverEvent(HoverEvent.showText(Component.text("Dar uma relíquia aleatória a jogadores")))
                            .clickEvent(ClickEvent.suggestCommand("/nexusadmin receber ")));
                    sender.sendMessage(Component.text("§c📊 /nexusadmin setlevel <level>")
                            .hoverEvent(HoverEvent.showText(Component.text("Definir nível da relíquia equipada")))
                            .clickEvent(ClickEvent.suggestCommand("/nexusadmin setlevel ")));
                    sender.sendMessage(Component.text("§c⚔️ /nexusadmin exp <true/false>")
                            .hoverEvent(HoverEvent.showText(Component.text("Ativar/desativar modo expurgo")))
                            .clickEvent(ClickEvent.suggestCommand("/nexusadmin exp ")));
                    sender.sendMessage(Component.text("§c🔒 /nexusadmin limite <valor>")
                            .hoverEvent(HoverEvent.showText(Component.text("Definir limite de relíquias por jogador")))
                            .clickEvent(ClickEvent.suggestCommand("/nexusadmin limite ")));
                    sender.sendMessage(Component.text("§c💰 /nexusadmin moly")
                            .hoverEvent(HoverEvent.showText(Component.text("Gerenciar sistema monetário")))
                            .clickEvent(ClickEvent.runCommand("/nexusadmin moly")));
                    return Command.SINGLE_SUCCESS;
                });

        // Comandos administrativos
        adminRoot.then(createRemoverCommand());
        adminRoot.then(createDarCommand());
        adminRoot.then(createReceberCommand());
        adminRoot.then(createSetLevelCommand());
        adminRoot.then(createExpCommand());
        adminRoot.then(createLimiteCommand());
        adminRoot.then(createMolyCommand());

        // Registrar comandos
        LiteralCommandNode<CommandSourceStack> playerCommand = playerRoot.build();
        LiteralCommandNode<CommandSourceStack> adminCommand = adminRoot.build();

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(playerCommand);
            commands.registrar().register(adminCommand);
        });

        // Registrar eventos
        getServer().getPluginManager().registerEvents(new JoinQuitEvent(this), this);
        getServer().getPluginManager().registerEvents(new LimitadorEvent(this), this);
        getServer().getPluginManager().registerEvents(new PassivaEvent(), this);
        getServer().getPluginManager().registerEvents(new PerdeuEvent(), this);
        getServer().getPluginManager().registerEvents(new EvoluirEvent(this), this);
        getServer().getPluginManager().registerEvents(new SpecialEvent(this), this);
        getServer().getPluginManager().registerEvents(this, this);

        getServer().getConsoleSender().sendMessage("§2✅ [Nexus]: Plugin Ativado!");
    }

    // Métodos para criar comandos
    private LiteralArgumentBuilder<CommandSourceStack> createLivroCommand() {
        return Commands.literal("livro")
                .executes(ctx -> {
                    final CommandSender sender = ctx.getSource().getSender();
                    if(ctx.getSource().getExecutor() instanceof Player player){
                        player.getInventory().addItem(ItemsRegistro.livro.getItem(1));
                        sender.sendMessage("§a📖 "+lang.getString("comandos.livro.sucesso"));
                    }else{
                        sender.sendMessage("§c❌ "+lang.getString("comandos.livro.erro"));
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> createEvoluirCommand() {
        return Commands.literal("evoluir")
                .executes(ctx -> {
                    final CommandSender sender = ctx.getSource().getSender();
                    if(ctx.getSource().getExecutor() instanceof Player player){
                        ItemStack stack = player.getInventory().getItemInMainHand();
                        ItemMeta meta = stack.getItemMeta();
                        if (meta == null) {
                            sender.sendMessage("§c❌ "+lang.getString("comandos.evoluir.erro1"));
                            return Command.SINGLE_SUCCESS;
                        }
                        PersistentDataContainer data = meta.getPersistentDataContainer();
                        if(data.has(NEXUS.key,PersistentDataType.STRING)){
                            EvoluirEvent evo = new EvoluirEvent(this);
                            String nome = data.get(NEXUS.key,PersistentDataType.STRING);
                            if(nome!=null){
                                PersistentDataContainer dataPlayer = player.getPersistentDataContainer();
                                int level=switch (nome){
                                    case "barbaro" -> dataPlayer.getOrDefault(BARBARO.key,PersistentDataType.INTEGER,1);
                                    case "ceifador" -> dataPlayer.getOrDefault(CEIFADOR.key,PersistentDataType.INTEGER,1);
                                    case "fazendeiro" -> dataPlayer.getOrDefault(FAZENDEIRO.key,PersistentDataType.INTEGER,1);
                                    case "guerreiro" -> dataPlayer.getOrDefault(GUERREIRO.key,PersistentDataType.INTEGER,1);
                                    case "mares" -> dataPlayer.getOrDefault(MARES.key,PersistentDataType.INTEGER,1);
                                    case "vida" -> dataPlayer.getOrDefault(VIDA.key,PersistentDataType.INTEGER,1);
                                    case "espiao" -> dataPlayer.getOrDefault(ESPIAO.key,PersistentDataType.INTEGER,1);
                                    case "arqueiro" -> dataPlayer.getOrDefault(ARQUEIRO.key,PersistentDataType.INTEGER,1);
                                    case "cacador" -> dataPlayer.getOrDefault(CACADOR.key,PersistentDataType.INTEGER,1);
                                    case "tempestade" -> dataPlayer.getOrDefault(TEMPESTADE.key,PersistentDataType.INTEGER,1);
                                    case "mineiro" -> dataPlayer.getOrDefault(MINEIRO.key,PersistentDataType.INTEGER,1);
                                    case "fenix" -> dataPlayer.getOrDefault(FENIX.key,PersistentDataType.INTEGER,1);
                                    case "protetor" -> dataPlayer.getOrDefault(PROTETOR.key,PersistentDataType.INTEGER,1);
                                    case "hulk" -> dataPlayer.getOrDefault(HULK.key,PersistentDataType.INTEGER,1);
                                    case "sculk" -> dataPlayer.getOrDefault(SCULK.key,PersistentDataType.INTEGER,1);
                                    case "pescador" -> dataPlayer.getOrDefault(PESCADOR.key,PersistentDataType.INTEGER,1);
                                    case "flash" -> dataPlayer.getOrDefault(FLASH.key,PersistentDataType.INTEGER,1);
                                    case "mago" -> dataPlayer.getOrDefault(MAGO.key,PersistentDataType.INTEGER,1);
                                    case "ladrao" -> dataPlayer.getOrDefault(LADRAO.key,PersistentDataType.INTEGER,1);
                                    case "domador" -> dataPlayer.getOrDefault(DOMADOR.key,PersistentDataType.INTEGER,1);
                                    default -> 1;
                                };
                                evo.tentarEvoluir(player,stack,level,evo.getSlotOfItem(player,stack));
                            }else{
                                sender.sendMessage("§c❌ "+lang.getString("comandos.evoluir.erro1"));
                            }
                        }else{
                            sender.sendMessage("§c❌ "+lang.getString("comandos.evoluir.erro1"));
                        }
                    }else{
                        sender.sendMessage("§c❌ "+lang.getString("comandos.evoluir.erro2"));
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> createMissoesCommand() {
        return Commands.literal("missoes")
                .executes(ctx -> {
                    final CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§c❌ Este comando só pode ser usado por um jogador.");
                        return Command.SINGLE_SUCCESS;
                    }

                    // Criar inventário de missões
                    Inventory missoesInv = Bukkit.createInventory(null, 54, Component.text("§5🎯 Missões Diárias"));

                    // Adicionar missões disponíveis
                    List<Missao> missoesDisponiveis = getMissoesDisponiveis();
                    for (int i = 0; i < missoesDisponiveis.size() && i < 45; i++) {
                        Missao missao = missoesDisponiveis.get(i);
                        ItemStack item = new ItemStack(missao.getIcone());
                        ItemMeta meta = item.getItemMeta();

                        meta.displayName(Component.text("§6" + missao.getNome()));

                        List<Component> lore = new ArrayList<>();
                        lore.add(Component.text("§7" + missao.getDescricao()));
                        lore.add(Component.text(""));
                        lore.add(Component.text("§e📋 Objetivos:"));
                        for (String objetivo : missao.getObjetivos()) {
                            lore.add(Component.text("§7• " + objetivo));
                        }
                        lore.add(Component.text(""));
                        lore.add(Component.text("§a💰 Recompensa: §e" + missao.getRecompensa() + " Moly"));
                        lore.add(Component.text("§b⏰ Tempo restante: §e" + formatarTempoRestante(missao.getTempoExpiracao())));
                        lore.add(Component.text(""));
                        lore.add(Component.text("§a✅ Clique para aceitar esta missão"));

                        meta.lore(lore);
                        item.setItemMeta(meta);
                        missoesInv.setItem(i, item);
                    }

                    // Item de fechar
                    ItemStack fechar = new ItemStack(Material.BARRIER);
                    ItemMeta fecharMeta = fechar.getItemMeta();
                    fecharMeta.displayName(Component.text("§c✖ Fechar"));
                    fechar.setItemMeta(fecharMeta);
                    missoesInv.setItem(49, fechar);

                    player.openInventory(missoesInv);
                    return Command.SINGLE_SUCCESS;
                });
    }

    private List<Missao> getMissoesDisponiveis() {
        List<Missao> missoes = new ArrayList<>();

        // Missão 1: Coletar recursos
        missoes.add(new Missao("Minerador Profissional",
                "Colete recursos valiosos das profundezas",
                500,
                Material.DIAMOND_PICKAXE,
                Arrays.asList("Minere 10 diamantes", "Minere 20 ferro", "Minere 30 carvão"),
                120));

        // Missão 2: Caçador
        missoes.add(new Missao("Caçador de Monstros",
                "Elimine criaturas perigosas",
                400,
                Material.IRON_SWORD,
                Arrays.asList("Derrote 5 zumbis", "Derrote 3 esqueletos", "Derrote 1 creeper"),
                90));

        // Missão 3: Fazendeiro
        missoes.add(new Missao("Fazendeiro Expert",
                "Cultive uma plantação abundante",
                350,
                Material.WHEAT,
                Arrays.asList("Plante 64 sementes", "Colha 32 trigo", "Crie 16 pães"),
                60));

        // Missão 4: Explorador
        missoes.add(new Missao("Explorador Intrépido",
                "Descubra novos territórios",
                600,
                Material.MAP,
                Arrays.asList("Visite 3 biomas diferentes", "Encontre uma vila", "Explore uma caverna"),
                180));

        return missoes;
    }

    private String formatarTempoRestante(long tempoExpiracao) {
        long segundosRestantes = (tempoExpiracao - System.currentTimeMillis()) / 1000;
        if (segundosRestantes <= 0) return "Expirada";

        long horas = segundosRestantes / 3600;
        long minutos = (segundosRestantes % 3600) / 60;

        if (horas > 0) {
            return horas + "h " + minutos + "m";
        } else {
            return minutos + " minutos";
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> createLojaCommand() {
        return Commands.literal("loja")
                .executes(ctx -> {
                    final CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§c❌ Este comando só pode ser usado por um jogador.");
                        return Command.SINGLE_SUCCESS;
                    }

                    // Criar ou obter loja do jogador
                    Loja loja = lojasAbertas.get(player.getUniqueId());
                    if (loja == null) {
                        loja = new Loja();
                        lojasAbertas.put(player.getUniqueId(), loja);
                    }

                    player.openInventory(loja.getInventario());
                    player.sendMessage("§a🏪 Loja aberta! Navegue pelos itens e clique para comprar.");
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> createListCommand() {
        return Commands.literal("list")
                .executes(ctx -> {
                    ConfigurationSection secao = config.getConfigurationSection("nexus");
                    if(secao!=null){
                        for(String nexus: secao.getKeys(false)){
                            String uuidStr = config.getString("nexus."+nexus);
                            String dono = "§c❌ "+lang.getString("comandos.list.sem");
                            if(uuidStr != null && !uuidStr.isBlank()){
                                try{
                                    UUID uuid = UUID.fromString(uuidStr);
                                    OfflinePlayer player = getServer().getOfflinePlayer(uuid);
                                    dono = (player.getName() != null? "§a✅ "+player.getName():"§c❌ "+lang.getString("comandos.list.desco"));
                                }catch(IllegalArgumentException ignored){
                                    dono = "§c❌ "+lang.getString("comandos.list.comro");
                                }
                            }
                            ctx.getSource().getSender().sendMessage("§b📋 "+nexus+": "+dono);
                        }
                    }else ctx.getSource().getSender().sendMessage("§c❌ "+lang.getString("comandos.list.erro"));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> createLevelCommand() {
        return Commands.literal("level")
                .executes(ctx -> {
                    if(ctx.getSource().getExecutor() instanceof Player player){
                        List<NamespacedKey> keys = NexusKeys.getKeyLevel();
                        PersistentDataContainer dataPlayer = player.getPersistentDataContainer();
                        player.sendMessage("§6📊 "+lang.getString("comandos.level.msg"));
                        for(NamespacedKey k:keys){
                            int l = dataPlayer.getOrDefault(k, PersistentDataType.INTEGER,0);
                            if(l>0){
                                player.sendMessage("§a✅ "+k.getKey()+": "+l);
                            }else{
                                player.sendMessage("§c❌ "+k.getKey()+": "+lang.getString("comandos.level.sem"));
                            }
                        }
                    }else ctx.getSource().getSender().sendMessage("§c❌ "+lang.getString("comandos.level.erro"));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> createCarteiraCommand() {
        return Commands.literal("carteira")
                .executes(ctx -> {
                    final CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§c❌ Este comando só pode ser usado por um jogador.");
                        return Command.SINGLE_SUCCESS;
                    }

                    double moly = getMoly(player.getUniqueId());
                    double molyBanco = getMolyBanco(player.getUniqueId());

                    player.sendMessage("§6💰 === SUA CARTEIRA ===");
                    player.sendMessage("§e💵 Moly na carteira: §a" + moly);
                    player.sendMessage("§e🏦 Moly no banco: §a" + molyBanco);
                    player.sendMessage("§e💎 Total: §a" + (moly + molyBanco));

                    // Verificar se tem empréstimo
                    if (emprestimos.containsKey(player.getUniqueId())) {
                        Emprestimo emp = emprestimos.get(player.getUniqueId());
                        double valorDevido = emp.getValorDevido();
                        player.sendMessage("§c⚠️ EMPRÉSTIMO PENDENTE!");
                        player.sendMessage("§c🏦 Banco: " + emp.getBanco());
                        player.sendMessage("§c💸 Valor devido: " + valorDevido + " Moly");
                        player.sendMessage("§c💳 Use /nexus emprestimo pagar para quitar sua dívida");
                    }

                    player.sendMessage("§a🤔 Gostaria de fazer um empréstimo com o banco?");
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> createEmprestimoCommand() {
        return Commands.literal("emprestimo")
                .executes(ctx -> {
                    final CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§c❌ Este comando só pode ser usado por um jogador.");
                        return Command.SINGLE_SUCCESS;
                    }

                    player.sendMessage("§6💳 === SISTEMA DE EMPRÉSTIMOS ===");
                    player.sendMessage(Component.text("§a📝 /nexus emprestimo pedir")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para pedir um empréstimo")))
                            .clickEvent(ClickEvent.runCommand("/nexus emprestimo pedir")));
                    player.sendMessage(Component.text("§a💵 /nexus emprestimo pagar")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para pagar seu empréstimo")))
                            .clickEvent(ClickEvent.runCommand("/nexus emprestimo pagar")));
                    player.sendMessage(Component.text("§a📊 /nexus emprestimo info")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para ver informações do seu empréstimo")))
                            .clickEvent(ClickEvent.runCommand("/nexus emprestimo info")));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("pedir")
                        .executes(ctx -> {
                            final Player player = (Player) ctx.getSource().getExecutor();
                            if (emprestimos.containsKey(player.getUniqueId())) {
                                player.sendMessage("§c❌ Você já tem um empréstimo pendente!");
                                return Command.SINGLE_SUCCESS;
                            }

                            player.sendMessage("§6🏦 Qual banco você quer pedir empréstimo?");
                            player.sendMessage("§a📋 Bancos disponíveis:");

                            for (String bancoNome : bancos.keySet()) {
                                Banco banco = bancos.get(bancoNome);
                                if (banco.isAprovado()) {
                                    player.sendMessage("§e- " + bancoNome + " (Max: " + banco.getMaxEmprestimo() + " Moly)");
                                }
                            }
                            player.sendMessage("§e- 🏛️ Banco Central Nexus (Max: 2000 Moly)");

                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("banco", StringArgumentType.string())
                                .then(Commands.argument("valor", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> {
                                            final Player player = (Player) ctx.getSource().getExecutor();
                                            String bancoNome = ctx.getArgument("banco", String.class);
                                            double valor = ctx.getArgument("valor", Double.class);

                                            if (bancoNome.equalsIgnoreCase("Banco Central Nexus")) {
                                                if (valor > 2000) {
                                                    player.sendMessage("§c❌ O Banco Central só empresta até 2000 Moly!");
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                // Lógica para empréstimo do banco central
                                                Emprestimo emprestimo = new Emprestimo(player.getUniqueId(), "Banco Central Nexus", valor, 0.5);
                                                emprestimos.put(player.getUniqueId(), emprestimo);
                                                addMoly(player.getUniqueId(), valor);

                                                player.sendMessage("§a✅ Empréstimo concedido! Você recebeu " + valor + " Moly");
                                                player.sendMessage("§c⚠️ ATENÇÃO: Você tem 3 dias para pagar!");
                                                player.sendMessage("§c📅 Se passar os 3 dias você será taxado em 5% por dia!");
                                                player.sendMessage("§c❌ Se não pagar em 10 dias você não terá acesso ao banco nem trocas!!");
                                                player.sendMessage("§c💸 Valor total a pagar: " + emprestimo.getValorTotal() + " Moly");

                                            } else if (bancos.containsKey(bancoNome)) {
                                                Banco banco = bancos.get(bancoNome);
                                                if (!banco.isAprovado()) {
                                                    player.sendMessage("§c❌ Este banco não está aprovado!");
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                if (valor > banco.getMaxEmprestimo()) {
                                                    player.sendMessage("§c❌ Este banco só empresta até " + banco.getMaxEmprestimo() + " Moly!");
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                if (valor > banco.getSaldo()) {
                                                    player.sendMessage("§c❌ O banco não tem saldo suficiente!");
                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                // Verificar chance de sucesso
                                                Random random = new Random();
                                                if (random.nextDouble() > banco.getTaxaSucesso()) {
                                                    player.sendMessage("§c❌ Seu pedido de empréstimo foi negado!");
                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                Emprestimo emprestimo = new Emprestimo(player.getUniqueId(), bancoNome, valor, banco.getTaxaJuros());
                                                emprestimos.put(player.getUniqueId(), emprestimo);
                                                banco.setSaldo(banco.getSaldo() - valor);
                                                addMoly(player.getUniqueId(), valor);

                                                player.sendMessage("§a✅ Empréstimo concedido! Você recebeu " + valor + " Moly");
                                                player.sendMessage("§c⚠️ ATENÇÃO: Você tem 3 dias para pagar!");
                                                player.sendMessage("§c💸 Valor total a pagar: " + emprestimo.getValorTotal() + " Moly");

                                            } else {
                                                player.sendMessage("§c❌ Banco não encontrado!");
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .then(Commands.literal("pagar")
                        .executes(ctx -> {
                            final Player player = (Player) ctx.getSource().getExecutor();
                            if (!emprestimos.containsKey(player.getUniqueId())) {
                                player.sendMessage("§c❌ Você não tem empréstimos pendentes!");
                                return Command.SINGLE_SUCCESS;
                            }

                            Emprestimo emprestimo = emprestimos.get(player.getUniqueId());
                            double valorDevido = emprestimo.getValorDevido();
                            double moly = getMoly(player.getUniqueId());

                            if (moly >= valorDevido) {
                                removeMoly(player.getUniqueId(), valorDevido);
                                emprestimos.remove(player.getUniqueId());

                                // Devolver o valor ao banco
                                if (!emprestimo.getBanco().equals("Banco Central Nexus")) {
                                    Banco banco = bancos.get(emprestimo.getBanco());
                                    if (banco != null) {
                                        banco.setSaldo(banco.getSaldo() + valorDevido);
                                    }
                                }

                                player.sendMessage("§a✅ Empréstimo pago com sucesso!");
                            } else {
                                player.sendMessage("§c❌ Você não tem Moly suficiente! Necessário: " + valorDevido);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            final Player player = (Player) ctx.getSource().getExecutor();
                            if (!emprestimos.containsKey(player.getUniqueId())) {
                                player.sendMessage("§a✅ Você não tem empréstimos pendentes!");
                                return Command.SINGLE_SUCCESS;
                            }

                            Emprestimo emprestimo = emprestimos.get(player.getUniqueId());
                            player.sendMessage("§6💳 === INFORMAÇÕES DO EMPRÉSTIMO ===");
                            player.sendMessage("§e🏦 Banco: " + emprestimo.getBanco());
                            player.sendMessage("§e💰 Valor original: " + emprestimo.getValor() + " Moly");
                            player.sendMessage("§e💸 Valor total a pagar: " + emprestimo.getValorTotal() + " Moly");
                            player.sendMessage("§e📅 Contratado em: " + new Date(emprestimo.getDataContracao()));

                            long diasPassados = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - emprestimo.getDataContracao());
                            if (diasPassados > 3) {
                                player.sendMessage("§c⚠️ ATRASO: " + (diasPassados - 3) + " dias");
                                player.sendMessage("§c💀 Multa acumulada: " + (emprestimo.getValorDevido() - emprestimo.getValorTotal()) + " Moly");
                            } else {
                                player.sendMessage("§a✅ Em dia! Dias restantes: " + (3 - diasPassados));
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                );
    }

    private LiteralArgumentBuilder<CommandSourceStack> createBancoCommand() {
        return Commands.literal("banco")
                .executes(ctx -> {
                    final CommandSender sender = ctx.getSource().getSender();
                    sender.sendMessage("§6🏦 === SISTEMA BANCÁRIO ===");
                    sender.sendMessage(Component.text("§a📊 /nexus banco info")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para ver informações dos bancos")))
                            .clickEvent(ClickEvent.runCommand("/nexus banco info")));
                    sender.sendMessage(Component.text("§a📝 /nexus banco criar-info")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para ver informações sobre como criar um banco")))
                            .clickEvent(ClickEvent.runCommand("/nexus banco criar-info")));
                    sender.sendMessage(Component.text("§a➕ /nexus banco criar")
                            .hoverEvent(HoverEvent.showText(Component.text("Clique para criar um banco")))
                            .clickEvent(ClickEvent.runCommand("/nexus banco criar")));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            final CommandSender sender = ctx.getSource().getSender();
                            sender.sendMessage("§6🏦 === BANCOS EXISTENTES ===");

                            if (bancos.isEmpty()) {
                                sender.sendMessage("§c❌ Nenhum banco cadastrado no momento.");
                                return Command.SINGLE_SUCCESS;
                            }

                            for (String nomeBanco : bancos.keySet()) {
                                Banco banco = bancos.get(nomeBanco);
                                if (banco.isAprovado()) {
                                    sender.sendMessage("§e" + nomeBanco + " - Saldo: §a" + banco.getSaldo() + " Moly");
                                    sender.sendMessage("§e  📈 Max Empréstimo: §a" + banco.getMaxEmprestimo() + " Moly");
                                    sender.sendMessage("§e  📊 Taxa de Sucesso: §a" + (banco.getTaxaSucesso() * 100) + "%");
                                }
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("criar-info")
                        .executes(ctx -> {
                            final CommandSender sender = ctx.getSource().getSender();
                            sender.sendMessage("§6📝 === INFORMAÇÕES PARA CRIAR BANCO ===");
                            sender.sendMessage("§a💰 Para criar um banco você precisa:");
                            sender.sendMessage("§e- Pagar 10k de Moly à vista 💵 OU");
                            sender.sendMessage("§e- Pagar em 10x de 1k Moly (1k a cada 5 dias) 📅");
                            sender.sendMessage("§e📊 Após pagar os 10k, precisará pagar 250 Moly de imposto a cada 5 dias");
                            sender.sendMessage("§e💸 Você pode fazer empréstimos para jogadores");
                            sender.sendMessage("§e🏪 Terá licença de loja especial");
                            sender.sendMessage("§a➕ Use /nexus banco criar para iniciar");
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("criar")
                        .executes(ctx -> {
                            final Player player = (Player) ctx.getSource().getExecutor();

                            // Verificar se já tem banco
                            for (Banco banco : bancos.values()) {
                                if (banco.getDono().equals(player.getUniqueId())) {
                                    player.sendMessage("§c❌ Você já tem um banco!");
                                    return Command.SINGLE_SUCCESS;
                                }
                            }

                            player.sendMessage("§6➕ === CRIAR BANCO ===");
                            player.sendMessage("§a💰 Escolha a forma de pagamento:");
                            player.sendMessage(Component.text("§e1 - Pagar 10k de Moly à vista 💵")
                                    .hoverEvent(HoverEvent.showText(Component.text("Clique para pagar à vista")))
                                    .clickEvent(ClickEvent.runCommand("/nexus banco criar 1")));
                            player.sendMessage(Component.text("§e2 - Pagar em 10x de 1k Moly 📅")
                                    .hoverEvent(HoverEvent.showText(Component.text("Clique para pagar parcelado")))
                                    .clickEvent(ClickEvent.runCommand("/nexus banco criar 2")));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("opcao", IntegerArgumentType.integer(1, 2))
                                .executes(ctx -> {
                                    final Player player = (Player) ctx.getSource().getExecutor();
                                    int opcao = ctx.getArgument("opcao", Integer.class);
                                    double moly = getMoly(player.getUniqueId());

                                    if (opcao == 1) {
                                        if (moly < 10000) {
                                            player.sendMessage("§c❌ Você não tem 10k Moly!");
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        removeMoly(player.getUniqueId(), 10000);
                                        player.sendMessage("§a✅ Pagamento realizado! Seu pedido foi enviado para análise.");

                                    } else if (opcao == 2) {
                                        if (moly < 1000) {
                                            player.sendMessage("§c❌ Você não tem 1k Moly para a primeira parcela!");
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        removeMoly(player.getUniqueId(), 1000);
                                        // Registrar pagamento parcelado
                                        config.set("banco.pagamento." + player.getUniqueId() + ".parcelas", 1);
                                        config.set("banco.pagamento." + player.getUniqueId() + ".total", 1000);
                                        saveConfig();

                                        player.sendMessage("§a✅ Primeira parcela paga! Restam 9 parcelas de 1k Moly.");
                                    }

                                    player.sendMessage("§a📨 Seu pedido foi enviado aos administradores!");
                                    player.sendMessage("§a👨‍💼 Eles verificarão sua autenticidade e aprovarão seu banco.");
                                    player.sendMessage("§a📱 Você será notificado via Discord sobre o status.");

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );
    }

    private LiteralArgumentBuilder<CommandSourceStack> createTrocarCommand() {
        return Commands.literal("trocar")
                .then(Commands.argument("jogador", ArgumentTypes.player())
                        .executes(ctx -> {
                            final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("jogador", PlayerSelectorArgumentResolver.class);
                            final Player p = targetResolver.resolve(ctx.getSource()).getFirst();
                            final CommandSender sender = ctx.getSource().getSender();

                            if(ctx.getSource().getExecutor() instanceof Player player){
                                ItemStack stack = player.getInventory().getItemInMainHand();
                                ItemMeta meta = stack.getItemMeta();
                                if (meta == null) {
                                    sender.sendMessage("§c❌ Você precisa estar segurando uma relíquia para trocar!");
                                    return Command.SINGLE_SUCCESS;
                                }

                                PersistentDataContainer data = meta.getPersistentDataContainer();
                                if(data.has(NEXUS.key,PersistentDataType.STRING)){
                                    String nome = data.get(NEXUS.key,PersistentDataType.STRING);
                                    if(nome!=null){
                                        Troca t = new Troca(player.getUniqueId(),nome);
                                        trocasPendentes.put(p.getUniqueId(),t);

                                        // Mensagem para o jogador que iniciou a troca
                                        player.sendMessage("§a🔄 Você ofereceu sua relíquia " + nome + " para " + p.getName());

                                        // Mensagem clicável para o jogador que recebeu a proposta
                                        Component mensagem = Component.text()
                                                .append(Component.text("§a🔄 " + player.getName() + " quer trocar a relíquia "))
                                                .append(Component.text("§6" + nome + " §acom você! "))
                                                .append(Component.text("§a[✅ ACEITAR]")
                                                        .hoverEvent(HoverEvent.showText(Component.text("§aClique para aceitar a troca")))
                                                        .clickEvent(ClickEvent.runCommand("/nexus aceitar")))
                                                .append(Component.text(" §7| "))
                                                .append(Component.text("§c[❌ CANCELAR]")
                                                        .hoverEvent(HoverEvent.showText(Component.text("§cClique para recusar a troca")))
                                                        .clickEvent(ClickEvent.runCommand("/nexus cancelar")))
                                                .build();

                                        p.sendMessage(mensagem);
                                    }
                                }else{
                                    sender.sendMessage("§c❌ Você precisa estar segurando uma relíquia válida!");
                                }
                            }else{
                                sender.sendMessage("§c❌ Este comando só pode ser usado por jogadores!");
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                );
    }

    private LiteralArgumentBuilder<CommandSourceStack> createAceitarCommand() {
        return Commands.literal("aceitar")
                .executes(ctx -> {
                    final CommandSender sender = ctx.getSource().getSender();
                    if(ctx.getSource().getExecutor() instanceof Player player){
                        Troca t = trocasPendentes.get(player.getUniqueId());
                        if (t == null) {
                            player.sendMessage("§c❌ Você não tem propostas de troca pendentes!");
                            return Command.SINGLE_SUCCESS;
                        }

                        Player p = Bukkit.getPlayer(t.uuid());
                        if (p == null || !p.isOnline()) {
                            player.sendMessage("§c❌ O jogador não está mais online!");
                            trocasPendentes.remove(player.getUniqueId());
                            return Command.SINGLE_SUCCESS;
                        }

                        ItemStack stack = player.getInventory().getItemInMainHand();
                        ItemMeta meta = stack.getItemMeta();
                        if (meta == null) {
                            player.sendMessage("§c❌ Você precisa estar segurando uma relíquia para aceitar a troca!");
                            return Command.SINGLE_SUCCESS;
                        }

                        PersistentDataContainer data = meta.getPersistentDataContainer();
                        if (!data.has(NEXUS.key, PersistentDataType.STRING)) {
                            player.sendMessage("§c❌ Você precisa estar segurando uma relíquia válida!");
                            return Command.SINGLE_SUCCESS;
                        }

                        String nomeReliquiaPlayer = data.get(NEXUS.key, PersistentDataType.STRING);

                        // Verificar se o jogador que propôs ainda tem a relíquia
                        boolean reliquiaEncontrada = false;
                        for (ItemStack item : p.getInventory().getContents()) {
                            if (item != null && item.hasItemMeta()) {
                                ItemMeta itemMeta = item.getItemMeta();
                                PersistentDataContainer itemData = itemMeta.getPersistentDataContainer();
                                if (itemData.has(NEXUS.key, PersistentDataType.STRING)) {
                                    String nomeReliquia = itemData.get(NEXUS.key, PersistentDataType.STRING);
                                    if (nomeReliquia.equals(t.stack())) {
                                        reliquiaEncontrada = true;
                                        break;
                                    }
                                }
                            }
                        }

                        if (!reliquiaEncontrada) {
                            player.sendMessage("§c❌ " + p.getName() + " não possui mais a relíquia " + t.stack() + "!");
                            trocasPendentes.remove(player.getUniqueId());
                            return Command.SINGLE_SUCCESS;
                        }

                        // Realizar a troca
                        if (realizarTroca(p, t.stack(), player, nomeReliquiaPlayer)) {
                            player.sendMessage("§a✅ Troca realizada com sucesso com " + p.getName() + "!");
                            p.sendMessage("§a✅ " + player.getName() + " aceitou a troca!");
                            trocasPendentes.remove(player.getUniqueId());
                        } else {
                            player.sendMessage("§c❌ Erro ao realizar a troca!");
                        }
                    } else {
                        sender.sendMessage("§c❌ Este comando só pode ser usado por jogadores!");
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> createCancelarCommand() {
        return Commands.literal("cancelar")
                .executes(ctx -> {
                    final CommandSender sender = ctx.getSource().getSender();
                    if(ctx.getSource().getExecutor() instanceof Player player){
                        Troca t = trocasPendentes.remove(player.getUniqueId());
                        if (t == null) {
                            player.sendMessage("§c❌ Você não tem propostas de troca pendentes!");
                            return Command.SINGLE_SUCCESS;
                        }

                        Player p = Bukkit.getPlayer(t.uuid());
                        if (p != null && p.isOnline()) {
                            p.sendMessage("§c❌ " + player.getName() + " recusou sua proposta de troca!");
                        }

                        player.sendMessage("§c❌ Você recusou a proposta de troca de " + (p != null ? p.getName() : "o jogador"));
                        return Command.SINGLE_SUCCESS;
                    } else {
                        sender.sendMessage("§c❌ Este comando só pode ser usado por jogadores!");
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> createServidorEconomiaCommand() {
        return Commands.literal("servidor-economia")
                .executes(ctx -> {
                    final CommandSender sender = ctx.getSource().getSender();
                    double totalMoly = getMolyTotalNoSistema();

                    sender.sendMessage("§6🏛️ === BANCO CENTRAL NEXUS ===");
                    sender.sendMessage("§e💰 Total de Moly no sistema: §a" + totalMoly);
                    sender.sendMessage("§a💸 Temos valor para emprestar para você!");
                    sender.sendMessage("§e📈 Limite máximo por empréstimo: §a2000 Moly");
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> createRemoverCommand() {
        return Commands.literal("remover")
                .then(Commands.argument("reliquia", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            names.stream().filter(entry -> entry.toLowerCase().startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("jogador", ArgumentTypes.player())
                                .executes(ctx -> {
                                    final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("jogador", PlayerSelectorArgumentResolver.class);
                                    final Player p = targetResolver.resolve(ctx.getSource()).getFirst();
                                    final CommandSender sender = ctx.getSource().getSender();
                                    final String reliquia = ctx.getArgument("reliquia", String.class).toLowerCase();

                                    Nexus n = ItemsRegistro.getFromNome(reliquia);
                                    if (n != null) {
                                        boolean reliquiaRemovida = false;
                                        PlayerInventory inv = p.getInventory();
                                        for (ItemStack item : inv.getContents()) {
                                            if (item != null && item.hasItemMeta()) {
                                                ItemMeta meta = item.getItemMeta();
                                                PersistentDataContainer data = meta.getPersistentDataContainer();
                                                if (data.has(NEXUS.key, PersistentDataType.STRING)) {
                                                    String nomeReliquia = data.get(NEXUS.key, PersistentDataType.STRING);
                                                    if (reliquia.equalsIgnoreCase(nomeReliquia)) {
                                                        inv.remove(item);
                                                        reliquiaRemovida = true;
                                                        break;
                                                    }
                                                }
                                            }
                                        }

                                        if (reliquiaRemovida) {
                                            config.set("nexus." + reliquia, null);
                                            saveConfig();
                                            PersistentDataContainer dataPlayer = p.getPersistentDataContainer();
                                            int qtd = dataPlayer.getOrDefault(QTD.key, PersistentDataType.INTEGER, 0);
                                            if (qtd > 0) {
                                                dataPlayer.set(QTD.key, PersistentDataType.INTEGER, qtd - 1);
                                            }
                                            sender.sendMessage("§a✅ Relíquia removida com sucesso!");
                                        } else {
                                            sender.sendMessage("§c❌ Jogador " + p.getName() + " não possui a relíquia " + reliquia + "!");
                                        }
                                    } else {
                                        sender.sendMessage("§c❌ Relíquia " + reliquia + " não existe!");
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))
                );
    }

    private LiteralArgumentBuilder<CommandSourceStack> createDarCommand() {
        return Commands.literal("dar")
                .then(Commands.argument("jogador", ArgumentTypes.player())
                        .then(Commands.argument("reliquia", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    names.stream().filter(entry -> entry.toLowerCase().startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("jogador", PlayerSelectorArgumentResolver.class);
                                    final Player p = targetResolver.resolve(ctx.getSource()).getFirst();
                                    final CommandSender sender = ctx.getSource().getSender();
                                    final String reliquia = ctx.getArgument("reliquia",String.class).toLowerCase();
                                    Nexus n = ItemsRegistro.getFromNome(reliquia);
                                    if(n!=null){
                                        int limite = config.getInt("limite");
                                        PersistentDataContainer dataPlayer = p.getPersistentDataContainer();
                                        int qtd = dataPlayer.getOrDefault(QTD.key, PersistentDataType.INTEGER,0);
                                        if(qtd>=limite){
                                            String m = lang.getString("comandos.receber.limite");
                                            if(m!=null){
                                                m=m.replace("<player>",p.getName());
                                                ctx.getSource().getSender().sendMessage("§c❌ "+m);
                                            }
                                        }else{
                                            qtd++;
                                            String nome = n.getNome();
                                            String uuidStr = config.getString("nexus."+nome);
                                            if(uuidStr==null || uuidStr.isBlank()){
                                                config.set("nexus."+nome,p.getUniqueId().toString());
                                                saveConfig();
                                                dataPlayer.set(QTD.key,PersistentDataType.INTEGER,qtd);
                                                int level =1;
                                                NamespacedKey key = NexusKeys.getKey(nome);
                                                if(key!=null && dataPlayer.has(key,PersistentDataType.INTEGER)){
                                                    level=dataPlayer.getOrDefault(key,PersistentDataType.INTEGER,1);
                                                }else if(key!=null){
                                                    dataPlayer.set(key,PersistentDataType.INTEGER,1);
                                                }
                                                ItemStack stack = n.getItem(level);
                                                ItemMeta meta = stack.getItemMeta();
                                                meta.getPersistentDataContainer().set(DONO.key,PersistentDataType.STRING,p.getUniqueId().toString());
                                                stack.setItemMeta(meta);
                                                p.getInventory().addItem(stack);
                                                p.sendMessage(Component.text("§a✅ "+lang.getString("comandos.receber.sucesso")+" "+nome));
                                                String m = lang.getString("comandos.receber.slog");
                                                if(m!=null){
                                                    m=m.replace("<player>",p.getName());
                                                    sender.sendMessage("§a✅ "+m+" "+nome);
                                                }
                                            }else{
                                                String m = lang.getString("comandos.receber.erro");
                                                if(m!=null){
                                                    m=m.replace("<relic>",reliquia);
                                                    sender.sendMessage("§c❌ "+m);
                                                }
                                            }
                                        }
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))
                );
    }

    private LiteralArgumentBuilder<CommandSourceStack> createReceberCommand() {
        return Commands.literal("receber")
                .then(Commands.argument("jogadores", ArgumentTypes.players())
                        .executes(ctx -> {
                            final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("jogadores", PlayerSelectorArgumentResolver.class);
                            final List<Player> targets = targetResolver.resolve(ctx.getSource());
                            final CommandSender sender = ctx.getSource().getSender();
                            int limite = config.getInt("limite");
                            for (final Player p : targets) {
                                PersistentDataContainer dataPlayer = p.getPersistentDataContainer();
                                int qtd = dataPlayer.getOrDefault(QTD.key, PersistentDataType.INTEGER,0);
                                if(qtd>=limite){
                                    String m = lang.getString("comandos.receber.limite");
                                    if(m!=null){
                                        m=m.replace("<player>",p.getName());
                                        ctx.getSource().getSender().sendMessage("§c❌ "+m);
                                    }
                                }else{
                                    qtd++;
                                    List<Nexus> reliquias = ItemsRegistro.getValidReliquia(config);
                                    Random rng = new Random();
                                    int escolhido = rng.nextInt(reliquias.size());
                                    Nexus n = reliquias.get(escolhido);
                                    String nome = n.getNome();
                                    config.set("nexus."+nome,p.getUniqueId().toString());
                                    saveConfig();
                                    dataPlayer.set(QTD.key,PersistentDataType.INTEGER,qtd);
                                    int level =1;
                                    NamespacedKey key = NexusKeys.getKey(nome);
                                    if(key!=null && dataPlayer.has(key,PersistentDataType.INTEGER)){
                                        level=dataPlayer.getOrDefault(key,PersistentDataType.INTEGER,1);
                                    }else if(key!=null){
                                        dataPlayer.set(key,PersistentDataType.INTEGER,1);
                                    }
                                    ItemStack stack = n.getItem(level);
                                    ItemMeta meta = stack.getItemMeta();
                                    meta.getPersistentDataContainer().set(DONO.key,PersistentDataType.STRING,p.getUniqueId().toString());
                                    stack.setItemMeta(meta);
                                    p.getInventory().addItem(stack);
                                    p.sendMessage(Component.text("§a✅ "+lang.getString("comandos.receber.sucesso")+" "+nome));
                                    String m = lang.getString("comandos.receber.slog");
                                    if(m!=null){
                                        m=m.replace("<player>",p.getName());
                                        sender.sendMessage("§a✅ "+m+" "+nome);
                                    }
                                }
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> createSetLevelCommand() {
        return Commands.literal("setlevel")
                .then(Commands.argument("level", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            final CommandSender sender = ctx.getSource().getSender();
                            if(ctx.getSource().getExecutor() instanceof Player player){
                                int level = ctx.getArgument("level", int.class);
                                ItemStack stack = player.getInventory().getItemInMainHand();
                                ItemMeta meta = stack.getItemMeta();
                                if (meta == null) {
                                    sender.sendMessage("§c❌ Você precisa estar segurando uma relíquia.");
                                    return Command.SINGLE_SUCCESS;
                                }
                                PersistentDataContainer data = meta.getPersistentDataContainer();
                                if(data.has(NEXUS.key,PersistentDataType.STRING)){
                                    String nome = data.get(NEXUS.key, PersistentDataType.STRING);
                                    if(nome!=null){
                                        NamespacedKey key = NexusKeys.getKey(nome);
                                        if(key!=null){
                                            player.getPersistentDataContainer().set(key,PersistentDataType.INTEGER,level);
                                            sender.sendMessage("§a✅ Nível da relíquia definido para " + level + "!");
                                        }
                                    }
                                } else {
                                    sender.sendMessage("§c❌ Você precisa estar segurando uma relíquia válida.");
                                }
                            }else {
                                sender.sendMessage("§c❌ "+lang.getString("comandos.level.erro"));
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> createExpCommand() {
        return Commands.literal("exp")
                .then(Commands.argument("exp", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean exp = ctx.getArgument("exp", boolean.class);
                            config.set("expurgo",exp);
                            saveConfig();
                            if(exp){
                                Bukkit.getOnlinePlayers().forEach(player -> {
                                    player.sendMessage("§c⚔️ "+lang.getString("comandos.expurgar.msg.aviso"));
                                    player.sendMessage("§c⚔️ "+lang.getString("comandos.expurgar.msg.perigo"));
                                    player.sendMessage("§c⚔️ "+lang.getString("comandos.expurgar.msg.aviso"));
                                });
                            }else{
                                Bukkit.getOnlinePlayers().forEach(player -> {
                                    player.sendMessage("§a🛡️ "+lang.getString("comandos.expurgar.msg.aviso"));
                                    player.sendMessage("§a🛡️ "+lang.getString("comandos.expurgar.msg.seguro"));
                                    player.sendMessage("§a🛡️ "+lang.getString("comandos.expurgar.msg.aviso"));
                                });
                            }
                            ctx.getSource().getSender().sendMessage("§a✅ "+lang.getString("comandos.expurgar.log")+" "+exp);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> createLimiteCommand() {
        return Commands.literal("limite")
                .then(Commands.argument("valor", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            final Integer valor = ctx.getArgument("valor", Integer.class);
                            final CommandSender sender = ctx.getSource().getSender();
                            if(valor<1){
                                sender.sendMessage("§c❌ "+lang.getString("comandos.limite.erro"));
                                return Command.SINGLE_SUCCESS;
                            }
                            config.set("limite",valor);
                            saveConfig();
                            sender.sendMessage("§a✅ "+lang.getString("comandos.limite.sucesso")+" "+valor);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> createMolyCommand() {
        return Commands.literal("moly")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    sender.sendMessage("§6💰 === SISTEMA MONETÁRIO ===");
                    sender.sendMessage(Component.text("§a➕ /nexusadmin moly adicionar <quantia> <alvo> <nome>")
                            .hoverEvent(HoverEvent.showText(Component.text("Adicionar Moly a jogador, banco ou economia")))
                            .clickEvent(ClickEvent.suggestCommand("/nexusadmin moly adicionar ")));
                    sender.sendMessage(Component.text("§c➖ /nexusadmin moly remover <quantia> <alvo> <nome>")
                            .hoverEvent(HoverEvent.showText(Component.text("Remover Moly de jogador ou banco")))
                            .clickEvent(ClickEvent.suggestCommand("/nexusadmin moly remover ")));
                    sender.sendMessage(Component.text("§e👀 /nexusadmin moly ver <alvo> <nome>")
                            .hoverEvent(HoverEvent.showText(Component.text("Ver saldo de jogador, banco ou economia")))
                            .clickEvent(ClickEvent.suggestCommand("/nexusadmin moly ver ")));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("adicionar")
                        .then(Commands.argument("quantia", DoubleArgumentType.doubleArg(0))
                                .then(Commands.argument("alvo", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("jogador");
                                            builder.suggest("banco");
                                            builder.suggest("economia");
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("nome", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    double quantia = ctx.getArgument("quantia", Double.class);
                                                    String alvo = ctx.getArgument("alvo", String.class);
                                                    String nome = ctx.getArgument("nome", String.class);
                                                    CommandSender sender = ctx.getSource().getSender();

                                                    switch (alvo.toLowerCase()) {
                                                        case "jogador":
                                                            Player alvoJogador = Bukkit.getPlayer(nome);
                                                            if (alvoJogador != null) {
                                                                addMoly(alvoJogador.getUniqueId(), quantia);
                                                                sender.sendMessage("§a✅ Adicionado " + quantia + " Moly para " + alvoJogador.getName());
                                                                alvoJogador.sendMessage("§a🎉 Você recebeu " + quantia + " Moly de um administrador!");
                                                            } else {
                                                                sender.sendMessage("§c❌ Jogador não encontrado: " + nome);
                                                            }
                                                            break;

                                                        case "banco":
                                                            if (bancos.containsKey(nome)) {
                                                                Banco banco = bancos.get(nome);
                                                                banco.setSaldo(banco.getSaldo() + quantia);
                                                                sender.sendMessage("§a✅ Adicionado " + quantia + " Moly ao banco " + nome);
                                                            } else {
                                                                sender.sendMessage("§c❌ Banco não encontrado: " + nome);
                                                            }
                                                            break;

                                                        case "economia":
                                                            // Adicionar à economia geral (distribuir aleatoriamente)
                                                            double totalAdicionado = 0;
                                                            for (Player online : Bukkit.getOnlinePlayers()) {
                                                                if (new Random().nextBoolean()) { // 50% de chance
                                                                    double quantidadeIndividual = quantia / 10; // Distribuir
                                                                    addMoly(online.getUniqueId(), quantidadeIndividual);
                                                                    online.sendMessage("§a💸 Você recebeu " + quantidadeIndividual + " Moly do estímulo econômico!");
                                                                    totalAdicionado += quantidadeIndividual;
                                                                }
                                                            }
                                                            sender.sendMessage("§a✅ Adicionado " + totalAdicionado + " Moly à economia do servidor");
                                                            break;

                                                        default:
                                                            sender.sendMessage("§c❌ Alvo inválido. Use: jogador, banco ou economia");
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("remover")
                        .then(Commands.argument("quantia", DoubleArgumentType.doubleArg(0))
                                .then(Commands.argument("alvo", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("jogador");
                                            builder.suggest("banco");
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("nome", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    double quantia = ctx.getArgument("quantia", Double.class);
                                                    String alvo = ctx.getArgument("alvo", String.class);
                                                    String nome = ctx.getArgument("nome", String.class);
                                                    CommandSender sender = ctx.getSource().getSender();

                                                    switch (alvo.toLowerCase()) {
                                                        case "jogador":
                                                            Player alvoJogador = Bukkit.getPlayer(nome);
                                                            if (alvoJogador != null) {
                                                                removeMoly(alvoJogador.getUniqueId(), quantia);
                                                                sender.sendMessage("§a✅ Removido " + quantia + " Moly de " + alvoJogador.getName());
                                                                alvoJogador.sendMessage("§c⚠️ Foram removidos " + quantia + " Moly da sua conta por um administrador");
                                                            } else {
                                                                sender.sendMessage("§c❌ Jogador não encontrado: " + nome);
                                                            }
                                                            break;

                                                        case "banco":
                                                            if (bancos.containsKey(nome)) {
                                                                Banco banco = bancos.get(nome);
                                                                banco.setSaldo(Math.max(0, banco.getSaldo() - quantia));
                                                                sender.sendMessage("§a✅ Removido " + quantia + " Moly do banco " + nome);
                                                            } else {
                                                                sender.sendMessage("§c❌ Banco não encontrado: " + nome);
                                                            }
                                                            break;

                                                        default:
                                                            sender.sendMessage("§c❌ Alvo inválido. Use: jogador ou banco");
                                                    }
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("ver")
                        .then(Commands.argument("alvo", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("jogador");
                                    builder.suggest("banco");
                                    builder.suggest("economia");
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("nome", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String alvo = ctx.getArgument("alvo", String.class);
                                            String nome = ctx.getArgument("nome", String.class);
                                            CommandSender sender = ctx.getSource().getSender();

                                            switch (alvo.toLowerCase()) {
                                                case "jogador":
                                                    Player alvoJogador = Bukkit.getPlayer(nome);
                                                    if (alvoJogador != null) {
                                                        double moly = getMoly(alvoJogador.getUniqueId());
                                                        double molyBanco = getMolyBanco(alvoJogador.getUniqueId());
                                                        sender.sendMessage("§6💰 Saldo de " + alvoJogador.getName() + ":");
                                                        sender.sendMessage("§e💵 Moly na carteira: §a" + moly);
                                                        sender.sendMessage("§e🏦 Moly no banco: §a" + molyBanco);
                                                        sender.sendMessage("§e💎 Total: §a" + (moly + molyBanco));
                                                    } else {
                                                        sender.sendMessage("§c❌ Jogador não encontrado: " + nome);
                                                    }
                                                    break;

                                                case "banco":
                                                    if (bancos.containsKey(nome)) {
                                                        Banco banco = bancos.get(nome);
                                                        sender.sendMessage("§6🏦 Saldo do banco " + nome + ": §a" + banco.getSaldo() + " Moly");
                                                    } else {
                                                        sender.sendMessage("§c❌ Banco não encontrado: " + nome);
                                                    }
                                                    break;

                                                case "economia":
                                                    double totalEconomia = getMolyTotalNoSistema();
                                                    sender.sendMessage("§6🌐 Total de Moly na economia: §a" + totalEconomia + " Moly");
                                                    break;

                                                default:
                                                    sender.sendMessage("§c❌ Alvo inválido. Use: jogador, banco ou economia");
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                );
    }

    // Métodos auxiliares
    private boolean realizarTroca(Player jogador1, String reliquia1, Player jogador2, String reliquia2) {
        try {
            // Remover reliquias dos inventários
            removerReliquia(jogador1, reliquia1);
            

            // Adicionar relíquias trocadas
            Nexus nexus1 = ItemsRegistro.getFromNome(reliquia1);
            Nexus nexus2 = ItemsRegistro.getFromNome(reliquia2);

            if (nexus1 != null && nexus2 != null) {
                // Obter níveis atuais
                int nivel1 = getNivelReliquia(jogador1, reliquia1);
                int nivel2 = getNivelReliquia(jogador2, reliquia2);

                // Dar as relíquias
                darReliquia(jogador2, reliquia1, nivel1);
                darReliquia(jogador1, reliquia2, nivel2);

                // Atualizar configuração
                config.set("nexus." + reliquia1, jogador2.getUniqueId().toString());
                config.set("nexus." + reliquia2, jogador1.getUniqueId().toString());
                saveConfig();

                return true;
            }
        } catch (Exception e) {
            getLogger().warning("Erro ao realizar troca: " + e.getMessage());
        }
        return false;
    }

    private void removerReliquia(Player jogador1, String reliquia1) {
    }

    private void remouterReliquia(Player jogador, String reliquia) {
        for (ItemStack item : jogador.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                PersistentDataContainer data = meta.getPersistentDataContainer();
                if (data.has(NEXUS.key, PersistentDataType.STRING)) {
                    String nome = data.get(NEXUS.key, PersistentDataType.STRING);
                    if (reliquia.equals(nome)) {
                        jogador.getInventory().remove(item);
                        break;
                    }
                }
            }
        }
    }

    private int getNivelReliquia(Player jogador, String reliquia) {
        PersistentDataContainer data = jogador.getPersistentDataContainer();
        NamespacedKey key = NexusKeys.getKey(reliquia);
        if (key != null && data.has(key, PersistentDataType.INTEGER)) {
            return data.getOrDefault(key, PersistentDataType.INTEGER, 1);
        }
        return 1;
    }

    private void darReliquia(Player jogador, String reliquia, int nivel) {
        Nexus nexus = ItemsRegistro.getFromNome(reliquia);
        if (nexus != null) {
            ItemStack item = nexus.getItem(nivel);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(DONO.key, PersistentDataType.STRING, jogador.getUniqueId().toString());
            item.setItemMeta(meta);
            jogador.getInventory().addItem(item);
        }
    }

    private double getMoly(UUID playerId) {
        return config.getDouble("moly." + playerId, 0);
    }

    private double getMolyBanco(UUID playerId) {
        return config.getDouble("moly_banco." + playerId, 0);
    }

    private void addMoly(UUID playerId, double amount) {
        double current = getMoly(playerId);
        config.set("moly." + playerId, current + amount);
        saveConfig();
    }

    private void removeMoly(UUID playerId, double amount) {
        double current = getMoly(playerId);
        config.set("moly." + playerId, Math.max(0, current - amount));
        saveConfig();
    }

    private double getMolyTotalNoSistema() {
        double total = 0;
        ConfigurationSection section = config.getConfigurationSection("moly");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                total += section.getDouble(key);
            }
        }
        return total;
    }

    private void carregarDadosEconomicos() {
        // Carregar empréstimos
        ConfigurationSection emprestimosSection = config.getConfigurationSection("emprestimos");
        if (emprestimosSection != null) {
            for (String key : emprestimosSection.getKeys(false)) {
                UUID playerId = UUID.fromString(key);
                String banco = config.getString("emprestimos." + key + ".banco");
                double valor = config.getDouble("emprestimos." + key + ".valor");
                double juros = config.getDouble("emprestimos." + key + ".juros");
                long data = config.getLong("emprestimos." + key + ".data");

                Emprestimo emprestimo = new Emprestimo(playerId, banco, valor, juros);
                emprestimos.put(playerId, emprestimo);
            }
        }

        // Carregar bancos
        ConfigurationSection bancosSection = config.getConfigurationSection("bancos");
        if (bancosSection != null) {
            for (String nome : bancosSection.getKeys(false)) {
                UUID dono = UUID.fromString(config.getString("bancos." + nome + ".dono"));
                double saldo = config.getDouble("bancos." + nome + ".saldo");
                double maxEmprestimo = config.getDouble("bancos." + nome + ".maxEmprestimo");
                double taxaJuros = config.getDouble("bancos." + nome + ".taxaJuros");
                double taxaSucesso = config.getDouble("bancos." + nome + ".taxaSucesso");
                String descricao = config.getString("bancos." + nome + ".descricao");
                boolean aprovado = config.getBoolean("bancos." + nome + ".aprovado");

                Banco banco = new Banco(nome, dono, saldo);
                banco.setMaxEmprestimo(maxEmprestimo);
                banco.setTaxaJuros(taxaJuros);
                banco.setTaxaSucesso(taxaSucesso);
                banco.setDescricao(descricao);
                banco.setAprovado(aprovado);

                bancos.put(nome, banco);
            }
        }
    }

    private void carregarMissoes() {
        // Carregar missões ativas dos jogadores
        ConfigurationSection missoesSection = config.getConfigurationSection("missoes");
        if (missoesSection != null) {
            for (String key : missoesSection.getKeys(false)) {
                UUID playerId = UUID.fromString(key);
                String nome = config.getString("missoes." + key + ".nome");
                String descricao = config.getString("missoes." + key + ".descricao");
                double recompensa = config.getDouble("missoes." + key + ".recompensa");
                Material icone = Material.valueOf(config.getString("missoes." + key + ".icone"));
                List<String> objetivos = config.getStringList("missoes." + key + ".objetivos");
                long tempoExpiracao = config.getLong("missoes." + key + ".tempoExpiracao");

                // Verificar se a missão ainda não expirou
                if (System.currentTimeMillis() < tempoExpiracao) {
                    Missao missao = new Missao(nome, descricao, recompensa, icone, objetivos, 0);
                    missoesAtivas.put(playerId, missao);
                } else {
                    // Remover missão expirada
                    config.set("missoes." + key, null);
                }
            }
            saveConfig();
        }
    }

    private void salvarDadosEconomicos() {
        // Salvar empréstimos
        for (Map.Entry<UUID, Emprestimo> entry : emprestimos.entrySet()) {
            UUID playerId = entry.getKey();
            Emprestimo emprestimo = entry.getValue();

            config.set("emprestimos." + playerId + ".banco", emprestimo.getBanco());
            config.set("emprestimos." + playerId + ".valor", emprestimo.getValor());
            config.set("emprestimos." + playerId + ".juros", 0.5);
            config.set("emprestimos." + playerId + ".data", emprestimo.getDataContracao());
        }

        // Salvar bancos
        for (Map.Entry<String, Banco> entry : bancos.entrySet()) {
            String nome = entry.getKey();
            Banco banco = entry.getValue();

            config.set("bancos." + nome + ".dono", banco.getDono().toString());
            config.set("bancos." + nome + ".saldo", banco.getSaldo());
            config.set("bancos." + nome + ".maxEmprestimo", banco.getMaxEmprestimo());
            config.set("bancos." + nome + ".taxaJuros", banco.getTaxaJuros());
            config.set("bancos." + nome + ".taxaSucesso", banco.getTaxaSucesso());
            config.set("bancos." + nome + ".descricao", banco.getDescricao());
            config.set("bancos." + nome + ".aprovado", banco.isAprovado());
        }

        // Salvar missões
        for (Map.Entry<UUID, Missao> entry : missoesAtivas.entrySet()) {
            UUID playerId = entry.getKey();
            Missao missao = entry.getValue();

            config.set("missoes." + playerId + ".nome", missao.getNome());
            config.set("missoes." + playerId + ".descricao", missao.getDescricao());
            config.set("missoes." + playerId + ".recompensa", missao.getRecompensa());
            config.set("missoes." + playerId + ".icone", missao.getIcone().name());
            config.set("missoes." + playerId + ".objetivos", missao.getObjetivos());
            config.set("missoes." + playerId + ".tempoExpiracao", missao.getTempoExpiracao());
        }

        saveConfig();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // Sistema de loja
        if (title.equals("§6🏪 Loja de Relíquias")) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

            Loja loja = lojasAbertas.get(player.getUniqueId());
            if (loja == null) return;

            ItemLoja itemLoja = loja.getItens().get(event.getRawSlot());
            if (itemLoja != null) {
                // Tentar comprar o item
                double preco = itemLoja.getPreco();
                double molyPlayer = getMoly(player.getUniqueId());

                if (molyPlayer >= preco) {
                    // Verificar limite de relíquias
                    PersistentDataContainer dataPlayer = player.getPersistentDataContainer();
                    int qtd = dataPlayer.getOrDefault(QTD.key, PersistentDataType.INTEGER, 0);
                    int limite = config.getInt("limite", 3);

                    if (qtd >= limite) {
                        player.sendMessage("§c❌ Você atingiu o limite de " + limite + " relíquias!");
                        player.closeInventory();
                        return;
                    }

                    // Realizar compra
                    removeMoly(player.getUniqueId(), preco);
                    qtd++;
                    dataPlayer.set(QTD.key, PersistentDataType.INTEGER, qtd);

                    // Dar a relíquia
                    Nexus nexus = ItemsRegistro.getFromNome(itemLoja.getReliquia());
                    if (nexus != null) {
                        ItemStack item = nexus.getItem(itemLoja.getNivel());
                        ItemMeta meta = item.getItemMeta();
                        meta.getPersistentDataContainer().set(DONO.key, PersistentDataType.STRING, player.getUniqueId().toString());
                        item.setItemMeta(meta);
                        player.getInventory().addItem(item);

                        // Atualizar configuração
                        config.set("nexus." + itemLoja.getReliquia(), player.getUniqueId().toString());
                        saveConfig();

                        player.sendMessage("§a✅ Compra realizada! Você adquiriu " + itemLoja.getReliquia() + " por " + preco + " Moly");
                    }
                } else {
                    player.sendMessage("§c❌ Você não tem Moly suficiente! Necessário: " + preco);
                }
                player.closeInventory();
            }

            // Verificar se clicou para fechar
            if (event.getRawSlot() == 49) {
                player.closeInventory();
            }
        }

        // Sistema de missões
        if (title.equals("§5🎯 Missões Diárias")) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

            // Verificar se clicou para fechar
            if (event.getRawSlot() == 49) {
                player.closeInventory();
                return;
            }

            // Verificar se já tem missão ativa
            if (missoesAtivas.containsKey(player.getUniqueId())) {
                player.sendMessage("§c❌ Você já tem uma missão ativa!");
                player.closeInventory();
                return;
            }

            // Obter a missão clicada
            List<Missao> missoesDisponiveis = getMissoesDisponiveis();
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < missoesDisponiveis.size()) {
                Missao missao = missoesDisponiveis.get(slot);
                missoesAtivas.put(player.getUniqueId(), missao);

                player.sendMessage("§a✅ Missão aceita: " + missao.getNome());
                player.sendMessage("§7📋 " + missao.getDescricao());
                player.sendMessage("§e💰 Recompensa: " + missao.getRecompensa() + " Moly");
                player.sendMessage("§b⏰ Tempo: " + formatarTempoRestante(missao.getTempoExpiracao()));

                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Verificar nick personalizado
        String nomePersonalizado = config.getString("nomes." + player.getUniqueId());
        if (nomePersonalizado != null && !nomePersonalizado.isEmpty()) {
            player.setDisplayName(nomePersonalizado);
            player.setPlayerListName(nomePersonalizado);
            player.setCustomName(nomePersonalizado);
        }

        // Verificar empréstimos vencidos
        if (emprestimos.containsKey(player.getUniqueId())) {
            Emprestimo emprestimo = emprestimos.get(player.getUniqueId());
            long diasPassados = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - emprestimo.getDataContracao());

            if (diasPassados > 3) {
                player.sendMessage("§c⚠️ SEU EMPRÉSTIMO ESTÁ ATRASADO!");
                player.sendMessage("§c📅 Dias de atraso: " + (diasPassados - 3));
                player.sendMessage("§c💸 Valor atual devido: " + emprestimo.getValorDevido() + " Moly");
            }

            if (diasPassados > 10) {
                player.sendMessage("§4❌ ACESSO AO BANCO BLOQUEADO!");
                player.sendMessage("§4🚫 Você não pagou seu empréstimo em 10 dias!");
            }
        }

        // Verificar missões expiradas
        if (missoesAtivas.containsKey(player.getUniqueId())) {
            Missao missao = missoesAtivas.get(player.getUniqueId());
            if (missao.isExpirada()) {
                player.sendMessage("§c❌ Sua missão '" + missao.getNome() + "' expirou!");
                missoesAtivas.remove(player.getUniqueId());
                config.set("missoes." + player.getUniqueId(), null);
                saveConfig();
            }
        }
    }

    @Override
    public void onDisable() {
        saveConfig();
        salvarDadosEconomicos();
        getServer().getConsoleSender().sendMessage("§4❌ [Nexus]: Plugin Desativado!");
    }

    public static FileConfiguration getNexusConfig(){
        return config;
    }

    public static FileConfiguration getLang(){
        return lang;
    }

    public static void setConfigSave(String path,Object value){
        config.set(path,value);
    }

    public static void saiu(Player p){
        Troca t = trocasPendentes.remove(p.getUniqueId());
        if(t==null)return;
        Player player = Bukkit.getPlayer(t.uuid());
        if(player!=null){
            String m = lang.getString("comandos.troca.erro3");
            if(m!=null){
                m=m.replace("<player>",p.getName());
                player.sendMessage("§c❌ "+m);
            }
        }
    }
}
