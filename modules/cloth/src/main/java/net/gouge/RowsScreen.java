package net.gouge;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class RowsScreen extends Screen {

    public record Spec(String title, String hint, String[] labels, boolean kindColumn, String[] blank, boolean blocks) {
    }

    private record Info(Component name, boolean ok, ItemStack stack, String glyph, Identifier texture) {
    }

    private static final Pattern ID = Pattern.compile("fallback|#?[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final int CARD_H = 42;
    private static final int ROW = 46;
    private static final int ID_W = 170;
    private static final int VAL_W = 64;
    private static final int KIND_W = 130;
    private static final int SUGGEST_H = 22;
    private static final int SUGGEST_W = 210;
    private static final int SUGGEST_ROWS = 5;
    private static final int MAX_MATCHES = 400;
    private static final int PANEL = 0xD0101014;
    private static final int PANEL_LINE = 0xFF3C3C46;
    private static final int CARD = 0xFF1B1B22;
    private static final int CARD_LINE = 0xFF34343F;
    private static final int ACCENT = 0xFFFFAA00;

    private final Screen parent;
    private final Spec spec;
    private final List<String[]> rows;
    private final Consumer<List<String[]>> onDone;
    private final List<String> ids;
    private final List<EditBox> idBoxes = new ArrayList<>();
    private final List<String[]> shown = new ArrayList<>();
    private final Map<String, Info> infoCache = new HashMap<>();
    private int page;
    private EditBox suggestBox;
    private List<String> suggestions = List.of();
    private int suggestOffset;
    private int suggestTop;
    private String suggestQuery = "";
    private boolean draggingScroll;

    public RowsScreen(Screen parent, Spec spec, List<String[]> rows, Consumer<List<String[]>> onDone) {
        super(Component.literal(spec.title()));
        this.parent = parent;
        this.spec = spec;
        this.rows = rows;
        this.onDone = onDone;
        List<String> all = new ArrayList<>();
        if (spec.blocks()) {
            BuiltInRegistries.BLOCK.keySet().forEach(k -> all.add(k.toString()));
        } else {
            all.add("fallback");
            BuiltInRegistries.ITEM.keySet().forEach(k -> {
                if (k.getPath().endsWith("_pickaxe")) {
                    all.add(k.toString());
                }
            });
        }
        all.sort(null);
        this.ids = all;
    }

    private static String kindText(String kind) {
        return switch (kind) {
            case "hard" -> "Hard (you hang)";
            case "soft" -> "Soft (you slide)";
            default -> "Default";
        };
    }

    private static String nextKind(String kind) {
        return switch (kind) {
            case "default" -> "hard";
            case "hard" -> "soft";
            default -> "default";
        };
    }

    private static boolean valid(String id) {
        return ID.matcher(id).matches();
    }

    private int valuesWidth() {
        int n = spec.labels().length - 1;
        return spec.kindColumn() ? KIND_W + (n - 1) * (VAL_W + 6) : Math.max(0, n * VAL_W + (n - 1) * 6);
    }

    private int cardWidth() {
        return 36 + ID_W + 6 + valuesWidth() + 6 + 20 + 6;
    }

    private int panelWidth() {
        return Math.max(cardWidth() + 24, 360);
    }

    private int cardLeft() {
        return (width - cardWidth()) / 2;
    }

    private List<FormattedCharSequence> hintLines() {
        return font.split(Component.literal(spec.hint()), panelWidth() - 24);
    }

    private int rowsTop() {
        return 30 + hintLines().size() * 11 + 22;
    }

    private int perPage() {
        return Math.max(2, (height - 76 - rowsTop()) / ROW);
    }

    @Override
    protected void init() {
        idBoxes.clear();
        shown.clear();
        int perPage = perPage();
        int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int left = cardLeft();
        int cardY = rowsTop();
        for (int i = page * perPage; i < Math.min(rows.size(), (page + 1) * perPage); i++) {
            String[] row = rows.get(i);
            shown.add(row);
            int y = cardY + 6;
            EditBox id = new EditBox(font, left + 36, y, ID_W, 20, Component.literal(spec.labels()[0]));
            id.setMaxLength(128);
            id.setValue(row[0]);
            id.setTextColor(valid(row[0]) ? 0xFFE0E0E0 : 0xFFFF5555);
            id.setResponder(v -> {
                row[0] = v;
                id.setTextColor(valid(v) ? 0xFFE0E0E0 : 0xFFFF5555);
            });
            idBoxes.add(addRenderableWidget(id));
            int x = left + 36 + ID_W + 6;
            for (int c = 1; c < spec.labels().length; c++) {
                final int col = c;
                if (c == 1 && spec.kindColumn()) {
                    addRenderableWidget(Button.builder(Component.literal(kindText(row[1])), b -> {
                        row[1] = nextKind(row[1]);
                        b.setMessage(Component.literal(kindText(row[1])));
                    }).bounds(x, y, KIND_W, 20).build());
                    x += KIND_W + 6;
                } else {
                    EditBox v = new EditBox(font, x, y, VAL_W, 20, Component.literal(spec.labels()[c]));
                    v.setMaxLength(16);
                    v.setValue(row[c]);
                    v.setResponder(t -> row[col] = t);
                    addRenderableWidget(v);
                    x += VAL_W + 6;
                }
            }
            addRenderableWidget(Button.builder(Component.literal("X").withStyle(ChatFormatting.RED), b -> {
                rows.remove(row);
                rebuildWidgets();
            }).bounds(left + cardWidth() - 26, y, 20, 20).build());
            cardY += ROW;
        }
        int cx = width / 2;
        int buttons = height - 56;
        int bw = Math.max(60, Math.min(150, width / 2 - 10));
        addRenderableWidget(Button.builder(Component.literal("+ Add"), b -> {
            rows.add(spec.blank().clone());
            page = Integer.MAX_VALUE;
            rebuildWidgets();
        }).bounds(cx - 147, buttons, 80, 20).build());
        Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page--;
            rebuildWidgets();
        }).bounds(cx - 59, buttons, 24, 20).build());
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page++;
            rebuildWidgets();
        }).bounds(cx + 123, buttons, 24, 20).build());
        prev.active = page > 0;
        next.active = page < pages - 1;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            onDone.accept(rows);
            minecraft.setScreenAndShow(parent);
        }).bounds(cx - bw - 5, height - 30, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(cx + 5, height - 30, bw, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private EditBox focusedId() {
        for (EditBox b : idBoxes) {
            if (b.isFocused()) {
                return b;
            }
        }
        return null;
    }

    private List<String> matches(String text) {
        String q = text.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty() || q.startsWith("#")) {
            return List.of();
        }
        List<String> starts = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (String id : ids) {
            if (id.equals(q)) {
                return List.of();
            }
            String path = id.substring(id.indexOf(':') + 1);
            if (id.startsWith(q) || path.startsWith(q)) {
                starts.add(id);
            } else if (id.contains(q)) {
                contains.add(id);
            }
        }
        starts.addAll(contains);
        return starts.size() > MAX_MATCHES ? starts.subList(0, MAX_MATCHES) : starts;
    }

    private int visibleSuggestions() {
        return Math.min(suggestions.size(), SUGGEST_ROWS);
    }

    private int maxOffset() {
        return Math.max(0, suggestions.size() - SUGGEST_ROWS);
    }

    private boolean overDropdown(double x, double y) {
        return suggestBox != null && !suggestions.isEmpty() && x >= suggestBox.getX() && x < suggestBox.getX() + SUGGEST_W
                && y >= suggestTop && y < suggestTop + visibleSuggestions() * SUGGEST_H + 2;
    }

    private void scrollTo(double y) {
        double ratio = (y - suggestTop - 2) / (visibleSuggestions() * SUGGEST_H - 2.0);
        suggestOffset = Math.max(0, Math.min(maxOffset(), (int) Math.round(ratio * maxOffset())));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScroll) {
            scrollTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScroll) {
            draggingScroll = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (overDropdown(x, y) || (suggestBox != null && !suggestions.isEmpty() && suggestBox.isMouseOver(x, y))) {
            suggestOffset = Math.max(0, Math.min(maxOffset(), suggestOffset - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (overDropdown(event.x(), event.y())) {
            if (suggestions.size() > SUGGEST_ROWS && event.x() >= suggestBox.getX() + SUGGEST_W - 9) {
                draggingScroll = true;
                scrollTo(event.y());
                return true;
            }
            int idx = suggestOffset + (int) ((event.y() - suggestTop - 1) / SUGGEST_H);
            if (idx >= 0 && idx < suggestions.size()) {
                suggestBox.setValue(suggestions.get(idx));
                suggestions = List.of();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private Info info(String raw) {
        String id = raw == null ? "" : raw.trim();
        return infoCache.computeIfAbsent(id, this::lookup);
    }

    private Info lookup(String id) {
        if (id.isEmpty()) {
            return new Info(Component.literal("Type an id").withStyle(ChatFormatting.DARK_GRAY), false, null, "?", null);
        }
        if (id.equals("fallback")) {
            return new Info(Component.literal("Every other pickaxe").withStyle(ChatFormatting.GRAY), true, null, "*", null);
        }
        if (id.startsWith("#")) {
            return new Info(Component.literal("Tag (everything in it)").withStyle(ChatFormatting.GRAY), true, null, "#", null);
        }
        Identifier key = valid(id) ? Identifier.tryParse(id) : null;
        String kind = spec.blocks() ? "block" : "item";
        if (key != null && spec.blocks() && BuiltInRegistries.BLOCK.containsKey(key)) {
            var block = BuiltInRegistries.BLOCK.getValue(key);
            ItemStack st = stack(block.asItem());
            return new Info(Component.translatable(block.getDescriptionId()).withStyle(ChatFormatting.GREEN), true, st, "", st == null ? texture(key, "block") : null);
        }
        if (key != null && !spec.blocks() && BuiltInRegistries.ITEM.containsKey(key)) {
            var item = BuiltInRegistries.ITEM.getValue(key);
            ItemStack st = stack(item);
            return new Info(Component.translatable(item.getDescriptionId()).withStyle(ChatFormatting.GREEN), true, st, "", st == null ? texture(key, "item") : null);
        }
        return new Info(Component.literal("Unknown " + kind + " (ignored in game)").withStyle(ChatFormatting.RED), false, null, "?", null);
    }

    private Identifier texture(Identifier key, String folder) {
        Identifier tex = Identifier.tryParse(key.getNamespace() + ":textures/" + folder + "/" + key.getPath() + ".png");
        return tex != null && minecraft.getResourceManager().getResource(tex).isPresent() ? tex : null;
    }

    private static ItemStack stack(net.minecraft.world.item.Item item) {
        try {
            return new ItemStack(item);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        int pw = panelWidth();
        int px = (width - pw) / 2;
        g.fill(px, 6, px + pw, height - 6, PANEL);
        g.outline(px, 6, pw, height - 12, PANEL_LINE);

        g.centeredText(font, title, width / 2, 14, 0xFFFFD36B);
        int hy = 28;
        for (FormattedCharSequence line : hintLines()) {
            g.text(font, line, (width - font.width(line)) / 2, hy, 0xFFA0A0A8);
            hy += 11;
        }
        g.fill(px + 12, hy + 4, px + pw - 12, hy + 5, PANEL_LINE);

        int left = cardLeft();
        int labelY = rowsTop() - 12;
        g.text(font, spec.labels()[0], left + 38, labelY, ACCENT);
        int lx = left + 36 + ID_W + 6;
        for (int c = 1; c < spec.labels().length; c++) {
            g.text(font, spec.labels()[c], lx + 2, labelY, ACCENT);
            lx += (c == 1 && spec.kindColumn() ? KIND_W : VAL_W) + 6;
        }

        int cardY = rowsTop();
        for (String[] row : shown) {
            boolean hover = mouseX >= left && mouseX < left + cardWidth() && mouseY >= cardY && mouseY < cardY + CARD_H;
            g.fill(left, cardY, left + cardWidth(), cardY + CARD_H, hover ? 0xFF22222B : CARD);
            g.outline(left, cardY, cardWidth(), CARD_H, hover ? ACCENT : CARD_LINE);
            Info info = info(row[0]);
            g.fill(left + 6, cardY + 8, left + 30, cardY + 32, 0xFF0B0B0E);
            g.outline(left + 6, cardY + 8, 24, 24, info.ok() ? 0xFF5A5A66 : 0xFF70404A);
            if (info.stack() != null) {
                g.item(info.stack(), left + 10, cardY + 12);
            } else if (info.texture() != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, info.texture(), left + 10, cardY + 12, 0f, 0f, 16, 16, 16, 16);
            } else {
                String glyph = info.glyph().isEmpty() ? "?" : info.glyph();
                g.centeredText(font, glyph, left + 18, cardY + 16, info.ok() ? 0xFFB0B0B8 : 0xFFFF7070);
            }
            g.text(font, info.name(), left + 38, cardY + 30, 0xFFFFFFFF);
            cardY += ROW;
        }

        int footer = height - 62;
        g.fill(px + 12, footer, px + pw - 12, footer + 1, PANEL_LINE);
        int pages = Math.max(1, (rows.size() + perPage() - 1) / perPage());
        g.centeredText(font, "Page " + (page + 1) + " / " + pages + "   " + rows.size() + (rows.size() == 1 ? " entry" : " entries"),
                width / 2 + 44, height - 50, 0xFFA0A0A8);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        EditBox focused = focusedId();
        suggestBox = focused;
        suggestions = focused != null ? matches(focused.getValue()) : List.of();
        if (suggestions.isEmpty()) {
            return;
        }
        if (!focused.getValue().equals(suggestQuery)) {
            suggestQuery = focused.getValue();
            suggestOffset = 0;
        }
        suggestOffset = Math.max(0, Math.min(maxOffset(), suggestOffset));
        int visible = visibleSuggestions();
        int h = visible * SUGGEST_H + 2;
        int sx = focused.getX();
        int sy = focused.getY() + 22;
        if (sy + h > height - 8) {
            sy = focused.getY() - h - 2;
        }
        suggestTop = sy;
        g.nextStratum();
        g.fill(sx, sy, sx + SUGGEST_W, sy + h, 0xF0101014);
        g.outline(sx, sy, SUGGEST_W, h, ACCENT);
        for (int i = 0; i < visible; i++) {
            String id = suggestions.get(suggestOffset + i);
            Info info = info(id);
            int ly = sy + 1 + i * SUGGEST_H;
            boolean hover = mouseX >= sx && mouseX < sx + SUGGEST_W - 9 && mouseY >= ly && mouseY < ly + SUGGEST_H;
            if (hover) {
                g.fill(sx + 1, ly, sx + SUGGEST_W - 9, ly + SUGGEST_H, 0xFF3A3A46);
            }
            if (info.stack() != null) {
                g.item(info.stack(), sx + 4, ly + 3);
            } else if (info.texture() != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, info.texture(), sx + 4, ly + 3, 0f, 0f, 16, 16, 16, 16);
            } else {
                g.centeredText(font, "?", sx + 12, ly + 7, 0xFFB0B0B8);
            }
            g.text(font, info.name(), sx + 26, ly + 3, 0xFFFFFFFF);
            g.text(font, id, sx + 26, ly + 12, hover ? 0xFFFFFFA0 : 0xFF8A8A94);
        }
        if (suggestions.size() > SUGGEST_ROWS) {
            int trackX = sx + SUGGEST_W - 7;
            int trackY = sy + 2;
            int trackH = h - 4;
            g.fill(trackX, trackY, trackX + 4, trackY + trackH, 0xFF2A2A32);
            int thumbH = Math.max(12, trackH * SUGGEST_ROWS / suggestions.size());
            int thumbY = trackY + (trackH - thumbH) * suggestOffset / maxOffset();
            g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, ACCENT);
            g.text(font, suggestions.size() + " matches", sx + SUGGEST_W - 9 - font.width(suggestions.size() + " matches"), sy + h + 2, 0xFF8A8A94);
        }
    }
}
