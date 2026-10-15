package com.heima.pet.utils;

import com.heima.api.enums.InteractionType;
import com.heima.api.enums.Personality;
import com.heima.api.enums.Species;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 宠物互动消息工具类
 * 根据宠物种类（CAT/DOG）、性格（粘人/傲娇/贪吃/佛系）和互动类型（FEED/PET/BATHE）
 * 从预设模板中随机返回一条消息。
 */
public class PetMessageUtil {
    // 禁止实例化
    private PetMessageUtil() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    // ---------- 模板数据 ----------
    private static final Map<String, List<String>> TEMPLATES = new HashMap<>();

    static {
        // 键格式：PET_TYPE_PERSONALITY_INTERACTION
        // ---------- 猫 ----------
        // 粘人猫
        putTemplates("CAT_CLINGY_FEED",
                "喵~ 好吃好吃！谢谢主人！",
                "咪咪最爱主人喂食啦！",
                "喵呜～ 再来一点嘛～");
        putTemplates("CAT_CLINGY_PET",
                "呼噜呼噜～ 好舒服，不要停～",
                "喵～ 主人的手最温暖了！",
                "蹭蹭～ 再摸摸我的下巴嘛～");
        putTemplates("CAT_CLINGY_BATHE",
                "呜呜～ 虽然不喜欢水，但是主人帮我洗我就忍了～",
                "喵！水好凉……不过主人好温柔……",
                "洗完要记得给我吹干哦～");

        // 傲娇猫
        putTemplates("CAT_PROUD_FEED",
                "哼，勉强吃一点吧，不是因为你喂的哦！",
                "……味道还行，不过本喵可不稀罕。",
                "喵！既然你诚心诚意地喂了，我就大发慈悲地吃掉。");
        putTemplates("CAT_PROUD_PET",
                "别摸我！……好吧，就一下下。",
                "哼，本喵允许你摸三秒钟。",
                "（转过头）…其实…还挺舒服的…");
        putTemplates("CAT_PROUD_BATHE",
                "什么？！你敢给我洗澡？！……好吧，看在你这么诚恳的份上。",
                "喵！水好冷！……不过是你洗的话就算了。",
                "洗完要帮我擦得香香的，不然我生气了！");

        // 贪吃猫
        putTemplates("CAT_GLUTTON_FEED",
                "喵！！（疯狂干饭）",
                "好吃到飞起！再来一碗！",
                "只要有吃的，你就是我最好的主人！");
        putTemplates("CAT_GLUTTON_PET",
                "别打扰我吃饭！…嗯？摸完能给零食吗？",
                "（边吃边发出呼噜声）…抚摸不影响我干饭。",
                "如果是想用抚摸换零食，那我可以考虑一下。");
        putTemplates("CAT_GLUTTON_BATHE",
                "（一脸惊恐）洗澡会耽误我吃饭的！",
                "洗快一点！我还要去吃罐头！",
                "（泡在浴缸里还不忘叼着零食）…");

        // 佛系猫
        putTemplates("CAT_ZEN_FEED",
                "……（优雅地细嚼慢咽）",
                "食物即馈赠，感谢主人。",
                "喵…一切随缘，你喂我就吃。");
        putTemplates("CAT_ZEN_PET",
                "（闭眼享受）…万物皆空，唯有抚摸真实。",
                "你摸或者不摸，我都在这里，不悲不喜。",
                "喵…（缓慢地眨了一下眼）");
        putTemplates("CAT_ZEN_BATHE",
                "水亦是缘，洗去尘埃，洗净心灵。",
                "（淡定地泡在水中）…顺其自然。",
                "洗完更好晒太阳，善哉。");

        // ---------- 狗 ----------
        // 粘人狗
        putTemplates("DOG_CLINGY_FEED",
                "汪汪！好吃！主人最好了！",
                "（疯狂摇尾巴）再来一点吧！",
                "我最喜欢吃主人给的东西了！");
        putTemplates("DOG_CLINGY_PET",
                "汪！摸我！快摸我！好舒服～",
                "（翻肚皮）…不要停呀主人！",
                "你的手有魔力，一摸我就开心！");
        putTemplates("DOG_CLINGY_BATHE",
                "虽然有点怕水，但是主人陪我我就不怕！",
                "汪！洗澡也要跟主人贴贴！",
                "洗得香香的，待会儿主人一定会多抱抱我！");

        // 傲娇狗
        putTemplates("DOG_PROUD_FEED",
                "（哼了一声）…不差，但不是我喜欢的牌子。",
                "看在你这么殷勤的份上，我吃一口吧。",
                "汪！下次要买那个贵点的牌子！");
        putTemplates("DOG_PROUD_PET",
                "别以为拍拍头就能收买我……（尾巴却不自觉地摇了）",
                "哼，本汪今天心情好，允许你摸两下。",
                "…（表面淡定，内心狂喜）");
        putTemplates("DOG_PROUD_BATHE",
                "洗澡？！本王拒绝！…除非你求我。",
                "好吧，给你个面子，不过要洗得舒服一点！",
                "（走进浴室时还一脸傲娇）…");

        // 贪吃狗
        putTemplates("DOG_GLUTTON_FEED",
                "汪汪！（一头扎进食盆）",
                "好吃！好吃！还有吗？",
                "只要有吃的，我可以把世界抛在脑后！");
        putTemplates("DOG_GLUTTON_PET",
                "（边吃边哼唧）…唔…你摸你的，我吃我的。",
                "要是摸完能给个肉干，那就更好了！",
                "（用鼻子拱你的手）…是要给我吃的吗？");
        putTemplates("DOG_GLUTTON_BATHE",
                "（极力挣扎）我不要洗澡！我要吃肉！",
                "洗快点，我要赶回去吃晚饭！",
                "（叼着一块骨头走进浴室）…我要边洗边吃。");

        // 佛系狗
        putTemplates("DOG_ZEN_FEED",
                "（安静地吃完）…谢谢。",
                "食物是生命的馈赠，我感恩每一粒狗粮。",
                "慢慢吃，品味生活的美好。");
        putTemplates("DOG_ZEN_PET",
                "（舒服地趴着）…抚摸是最温柔的交流。",
                "你的手传递着善意，我感受到了。",
                "汪…（恬静地闭上眼睛）");
        putTemplates("DOG_ZEN_BATHE",
                "水洗净身体，也洗净心灵…（闭眼享受）",
                "洗个澡，然后找个阳光处睡一觉。",
                "汪…人生（狗生）如水，随遇而安。");
    }

    // 辅助方法：添加模板
    private static void putTemplates(String key, String... messages) {
        TEMPLATES.put(key, Arrays.asList(messages));
    }

    /**
     * 获取随机消息
     * @param petType 宠物种类
     * @param personality 性格
     * @param interaction 互动类型
     * @return 随机选取的消息，若无匹配则返回默认消息
     */
    public static String getRandomMessage(Species petType, Personality personality, InteractionType interaction) {
        String key = petType.name() + "_" + personality.name() + "_" + interaction.name();
        List<String> messages = TEMPLATES.get(key);
        if (messages == null || messages.isEmpty()) {
            return "（宠物发出了可爱的声音）"; // fallback
        }
        // 随机选择一个
        int index = ThreadLocalRandom.current().nextInt(messages.size());
        return messages.get(index);
    }
}
