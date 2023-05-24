package io.github.nickid2018.general;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Main {

    public static final Map<String, Set<String>> TAG_LIST = new HashMap<>();

    public static final Map<String, SpawnTable> SPAWN_TABLE = new HashMap<>();

    public static class SpawnTable {
        public List<SpawnRow> creature = new ArrayList<>();
        public List<SpawnRow> monster = new ArrayList<>();
        public List<SpawnRow> water_creature = new ArrayList<>();
        public List<SpawnRow> ambient = new ArrayList<>();

        public SpawnTable() {
            SpawnRow squid = new SpawnRow("Glow Squid", 10, "2-4");
            squid.note = "发光鱿鱼只能在地下的水体中生成。";
            creature.add(squid);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("{{in|be}}下列生物会在此生成：\n\n{{Spawn table\n");
            Comparator<SpawnRow> comparator = (a1, a2) -> {
                int i = Integer.compare(a2.weight, a1.weight);
                if (i == 0)
                    return a1.id.compareTo(a2.id);
                return i;
            };
            if (!monster.isEmpty()) {
                builder.append("|monster=\n");
                monster.stream().sorted(comparator).forEach(row -> builder.append(row).append("\n"));
            }
            if (!creature.isEmpty()) {
                builder.append("|creature=\n");
                creature.stream().sorted(comparator).forEach(row -> builder.append(row).append("\n"));
            }
            if (!water_creature.isEmpty()) {
                builder.append("|watercreature=\n");
                water_creature.stream().sorted(comparator).forEach(row -> builder.append(row).append("\n"));
            }
            if (!ambient.isEmpty()) {
                builder.append("|ambient=\n");
                ambient.stream().sorted(comparator).forEach(row -> builder.append(row).append("\n"));
            }
            builder.append("}}");
            return builder.toString();
        }
    }

    public static class SpawnRow {
        public String id;
        public int weight;
        public String size;
        public String note;
        public String noteName;

        public SpawnRow(String id, int weight, String size) {
            this.id = id;
            this.weight = weight;
            this.size = size;
        }

        @Override
        public String toString() {
            return note == null ?
                    noteName == null ?
                            "{{Spawn row|%s|weight=%d|size=%s}}".formatted(id, weight, size) :
                            "{{Spawn row|%s|weight=%d|size=%s|notename=%s}}".formatted(id, weight, size, noteName) :
                    "{{Spawn row|%s|weight=%d|size=%s|note=%s}}".formatted(id, weight, size, note);
        }
    }

    public static final String ROOT_PATH = "E:\\MC-Servers\\BE1.19.80.20\\";

    public static void main(String[] args) throws IOException {
        File biomeFile = new File(ROOT_PATH + "definitions\\biomes");
        for (File f : Objects.requireNonNull(biomeFile.listFiles())) {
            if (f.getName().endsWith(".json")) {
                JsonObject object = JsonParser.parseString(IOUtils.toString(f.toURI(), StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject root = object.getAsJsonObject("minecraft:biome");
                String id = resolveID(root.get("description").getAsJsonObject().get("identifier").getAsString());
                JsonObject tags = root.getAsJsonObject("components");
                Set<String> tagSet = new HashSet<>(tags.keySet());
                tagSet.removeIf(s -> s.startsWith("minecraft:"));
                TAG_LIST.put(id, tagSet);
                JsonObject climate = tags.getAsJsonObject("minecraft:climate");
                JsonArray snowAccumulation = climate.getAsJsonArray("snow_accumulation");
                if (snowAccumulation != null && snowAccumulation.get(0).getAsDouble() > 0)
                    TAG_LIST.get(id).add("is_snow_covered");
            }
        }

        Map<String, File> filteredSpawnRules = new HashMap<>();
        File bahaviorFile = new File(ROOT_PATH + "behavior_packs");
        File[] sorted = Arrays.stream(Objects.requireNonNull(bahaviorFile.listFiles())).sorted(
                Comparator.comparing(File::getName)
        ).sorted(
                (f1, f2) -> {
                    if (f1.getName().contains("experimental"))
                        return 1;
                    if (f2.getName().contains("experimental"))
                        return -1;
                    return 0;
                }
        ).toArray(File[]::new);
        for (File pack : sorted) {
            for (File def : Objects.requireNonNull(pack.listFiles())) {
                if (!def.getName().equals("spawn_rules"))
                    continue;
                for (File spawnRuleFile : Objects.requireNonNull(def.listFiles())) {
                    filteredSpawnRules.put(spawnRuleFile.getName(), spawnRuleFile);
                }
            }
        }

        for (File f : filteredSpawnRules.values()) {
            if (f.getName().endsWith(".json")) {
                JsonObject object = JsonParser.parseString(IOUtils.toString(f.toURI(), StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject root = object.getAsJsonObject("minecraft:spawn_rules");
                JsonObject mobInfo = root.getAsJsonObject("description");

                String id = resolveID(mobInfo.get("identifier").getAsString());
                String population = mobInfo.get("population_control").getAsString();

                if (!root.has("conditions"))
                    continue;
                if (id.equals("Phantom"))
                    continue;

                JsonArray conditions = root.getAsJsonArray("conditions");
                for (JsonElement element : conditions) {
                    JsonObject condition = element.getAsJsonObject();
                    int weight;
                    if (condition.has("minecraft:weight"))
                        weight = condition.getAsJsonObject("minecraft:weight").get("default").getAsInt();
                    else
                        weight = 100;
                    JsonElement herd = condition.get("minecraft:herd");
                    String size;
                    if (herd == null) {
                        size = "1";
                    } else if (herd.isJsonObject()) {
                        JsonObject herdSize = herd.getAsJsonObject();
                        int min = herdSize.get("min_size").getAsInt();
                        int max = herdSize.get("max_size").getAsInt();
                        size = min == max ? String.valueOf(min) : min + "-" + max;
                    } else {
                        JsonArray herdSize = herd.getAsJsonArray();
                        int min = herdSize.get(0).getAsJsonObject().getAsJsonPrimitive("min_size").getAsInt();
                        int max = herdSize.get(0).getAsJsonObject().getAsJsonPrimitive("max_size").getAsInt();
                        for (int i = 1; i < herdSize.size(); i++) {
                            JsonObject obj = herdSize.get(i).getAsJsonObject();
                            int min1 = obj.getAsJsonPrimitive("min_size").getAsInt();
                            int max1 = obj.getAsJsonPrimitive("max_size").getAsInt();
                            if (min1 != min || max1 != max)
                                throw new IllegalStateException("Herd size is not same!");
                        }
                        size = min == max ? String.valueOf(min) : min + "-" + max;
                    }
                    if (!condition.has("minecraft:biome_filter"))
                        continue;
                    JsonElement biome = condition.get("minecraft:biome_filter");
                    Set<String> biomeList = filterBiome(new HashSet<>(TAG_LIST.keySet()), biome);
                    List<SpawnRow> rows = new ArrayList<>();
                    if (condition.has("minecraft:permute_type")) {
                        JsonArray permute = condition.getAsJsonArray("minecraft:permute_type");
                        for (JsonElement e : permute) {
                            int w = e.getAsJsonObject().get("weight").getAsInt();
                            String type;
                            if (e.getAsJsonObject().has("entity_type"))
                                type = resolveID(e.getAsJsonObject().get("entity_type").getAsString());
                            else
                                type = id;
                            rows.add(new SpawnRow(type, w, size));
                        }
                    } else if (id.equals("Stray")) {
                        rows.add(new SpawnRow(id, (int) (weight * 0.8), size));
                        rows.add(new SpawnRow("Skeleton", (int) (weight * 0.2), size));
                    } else
                        rows.add(new SpawnRow(id, weight, size));

                    for (String b : biomeList) {
                        for (SpawnRow rowSource : rows) {
                            SpawnTable table = SPAWN_TABLE.computeIfAbsent(b, s -> new SpawnTable());
                            SpawnRow row = new SpawnRow(rowSource.id, rowSource.weight, rowSource.size);
                            if (row.id.equals("Slime"))
                                row.note = "只有在史莱姆区块中才能成功生成。";
                            List<SpawnRow> list = switch (population) {
                                case "animal" -> table.creature;
                                case "monster" -> table.monster;
                                case "water_animal" -> table.water_creature;
                                case "ambient" -> table.ambient;
                                case "pillager" -> new ArrayList<>();
                                default -> throw new IllegalStateException("Unexpected value: " + population);
                            };

                            List<SpawnRow> same = list.stream().filter(r -> r.id.equals(row.id)).toList();
                            if (same.isEmpty())
                                list.add(row);
                            else {
                                same.get(0).note = id + "分" + (same.size() + 1) + "次生成。";
                                for (int i = 1; i < same.size(); i++)
                                    same.get(i).noteName = id.toLowerCase();
                                row.noteName = id.toLowerCase();
                                list.add(row);
                            }
                        }
                    }
                }
            }
        }

        Map<String, Set<String>> sameTags = new HashMap<>();
        for (String id : TAG_LIST.keySet()) {
            String outputs = SPAWN_TABLE.get(id).toString();
            sameTags.computeIfAbsent(outputs, s -> new HashSet<>()).add(id);
        }

        for (Set<String> biomes : sameTags.values()) {
            System.out.println(biomes);
        }

        System.out.println(SPAWN_TABLE.get("Cherry Grove"));
    }

    public static String resolveID(String id) {
        String source = id.substring(id.indexOf(':') + 1);
        if (source.contains("_"))
            source = source.replace("_", " ");
        List<String> words = Arrays.stream(source.split(" "))
                .map(String::toLowerCase)
                .map(s -> Character.toTitleCase(s.charAt(0)) + s.substring(1)).toList();
        String p1 = String.join(" ", words);
        if (p1.endsWith("V2"))
            p1 = p1.substring(0, p1.length() - 3);
        return p1;
    }

    public static Set<String> filterBiome(Set<String> source, JsonElement element) {
        if (element.isJsonObject() && element.getAsJsonObject().has("any_of")) {
            JsonArray array = element.getAsJsonObject().getAsJsonArray("any_of");
            Set<String> result = new HashSet<>();
            for (JsonElement e : array)
                result.addAll(filterBiome(source, e));
            return result;
        } else {
            JsonArray filterEntries = new JsonArray();
            if (element.isJsonObject() && element.getAsJsonObject().has("all_of")) {
                filterEntries = element.getAsJsonObject().getAsJsonArray("all_of");
            } else if (element.isJsonArray()) {
                filterEntries = element.getAsJsonArray();
            } else
                filterEntries.add(element);
            Set<String> result = new HashSet<>(source);
            for (JsonElement e : filterEntries) {
                JsonObject filterEntry = e.getAsJsonObject();
                if (!filterEntry.has("test"))
                    result = filterBiome(result, filterEntry);
                else {
                    String filterType = filterEntry.get("test").getAsString();
                    if (filterType.equals("has_biome_tag")) {
                        String value = filterEntry.get("value").getAsString();
                        boolean contains = filterEntry.get("operator").getAsString().equals("==");
                        if (contains)
                            result.removeIf(s -> !TAG_LIST.get(s).contains(value));
                        else
                            result.removeIf(s -> TAG_LIST.get(s).contains(value));
                    } else if (filterType.equals("is_snow_covered")) {
                        result.removeIf(s -> !TAG_LIST.get(s).contains("is_snow_covered"));
                    } else {
                        System.err.println("Unknown filter type: " + filterType);
                        result.clear();
                    }
                }
            }
            return result;
        }
    }
}
