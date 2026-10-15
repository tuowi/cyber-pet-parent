package com.heima.social.utils;

import com.heima.api.enums.InteractionType;
import com.heima.api.enums.Personality;
import com.heima.api.enums.SocialInteractionType;
import com.heima.api.enums.Species;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 宠物互动消息工具类（针对偷零食、玩球、拜访三种互动）
 * 根据宠物种类（CAT/DOG）、性格（CLINGY/PROUD/GLUTTON/ZEN）和互动类型
 * 从预设模板中随机返回一条消息。
 */
public class PetSocialMessageUtil {
    private PetSocialMessageUtil() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    // ---------- 模板数据 ----------
    private static final Map<String, List<String>> TEMPLATES = new HashMap<>();

    static {
        // 键格式：PET_TYPE_PERSONALITY_INTERACTION
        // ---------- 猫 ----------
        // 粘人猫（CLINGY）
        putTemplates("CAT_CLINGY_STEAL_SNACK",
                "喵呜！！我的小鱼干被偷了！！主人快帮我找回来嘛～（蹭蹭蹭）",
                "喵！！谁这么坏偷我零食！主人～抱抱我安慰一下嘛～",
                "呜呜喵！我的零食不见了！主人陪我一起去找嘛！",
                "喵～主人～我的小鱼干没了...你帮我报仇好不好嘛～（眼巴巴）");
        putTemplates("CAT_CLINGY_PLAY_MATCH",
                "喵！主人来玩嘛！球球在这～快扔！",
                "喵喵～陪我玩一会儿嘛～就一会儿～",
                "主人主人！看我看我！我超会接球的！",
                "玩球玩球！主人你最好了！快陪我玩嘛！");
        putTemplates("CAT_CLINGY_VISIT",
                "喵～家里来客人了？主人我躲你后面...",
                "有陌生人！主人保护我！（缩在主人脚边）",
                "喵...他们是谁呀...主人抱我嘛...",
                "来人了？主人我先躲一下...你叫我了再出来哦～");

        // 傲娇猫（PROUD）
        putTemplates("CAT_PROUD_STEAL_SNACK",
                "哼！本喵的小鱼干也敢偷？不想活了是吧！",
                "区区小鱼干...罢了！本喵才不稀罕！...（偷偷看空盘子）",
                "呵，偷我零食？行，你等着。（眯眼）",
                "本喵的零食也敢动？你很有胆量嘛。（冷笑）");
        putTemplates("CAT_PROUD_PLAY_MATCH",
                "无聊...不过既然你求我，陪你玩一下好了。",
                "哼，这球一点意思都没有...（尾巴却疯狂摆动）",
                "就陪你玩五分钟，多一秒都不行！",
                "想玩？行吧...看你这么诚恳的份上，本喵就勉为其难一下。");
        putTemplates("CAT_PROUD_VISIT",
                "哼，来客人了也不提前说一声...（优雅走开）",
                "本喵才不关心谁来呢...（但偷偷观察）",
                "哦，有人来了啊...（高冷地瞟了一眼）",
                "客人？嗯...知道了。（继续舔爪子）");

        // 贪吃猫（GLUTTON）
        putTemplates("CAT_GLUTTON_STEAL_SNACK",
                "喵啊啊啊！！我的小鱼干！！全都没了！！谁干的！！",
                "完蛋了完蛋了！我的零食储备被掏空了！天塌了！",
                "谁！！谁动了我藏在沙发底下的零食！！我跟你拼了！",
                "小鱼干！我的小鱼干！全部！一个都不剩！喵呜呜呜！");
        putTemplates("CAT_GLUTTON_PLAY_MATCH",
                "玩可以...但得先给我零食！",
                "喵！追到球球有奖励吗？小鱼干？",
                "唔...玩一下也行，但等会要给我好吃的！",
                "先给零食再玩！这是规矩！");
        putTemplates("CAT_GLUTTON_VISIT",
                "来客人了？带礼物了吗？带吃的了吗？",
                "喵～新面孔！有零食吗！（期待眼神）",
                "客人来了...会投喂我吗？（暗中观察）",
                "新朋友！你好！你手里拿的是...吃的吗？！");

        // 佛系猫（ZEN）
        putTemplates("CAT_ZEN_STEAL_SNACK",
                "喵...零食没了...算了，缘起缘灭...",
                "随他去吧...本就是身外之物...（但尾巴在颤抖）",
                "罢了罢了...命里有时终须有，命里无时莫强求...",
                "小鱼干...没了...哦...（缓缓闭上眼睛）");
        putTemplates("CAT_ZEN_PLAY_MATCH",
                "玩？...嗯...也行...（缓缓伸了个懒腰）",
                "球放着吧...想动的时候我会动的...",
                "动一下...好累...不玩了...",
                "你玩吧...我看着就好...（趴下）");
        putTemplates("CAT_ZEN_VISIT",
                "哦，有人来了...（继续眯眼睡觉）",
                "客人啊...随意坐...我先睡了...",
                "来了就来了吧...世界就是这样运转的...",
                "嗯...有声音...（耳朵动了动，继续睡）");

        // ---------- 狗 ----------
        // 粘人狗（CLINGY）
        putTemplates("DOG_CLINGY_STEAL_SNACK",
                "汪汪！！我的肉干被偷了！主人你要给我做主啊！（摇尾巴蹭主人）",
                "呜呜...零食没了...主人能抱抱我吗...",
                "谁偷了我的骨头！！主人帮我找找嘛～",
                "汪！我的零食！！主人我好难过啊...（把脑袋搭在主人腿上）");
        putTemplates("DOG_CLINGY_PLAY_MATCH",
                "汪汪！主人你终于要陪我玩了！！太开心了！！",
                "球！！扔球！！我准备好了！！",
                "玩嘛玩嘛玩嘛！！我超想玩的！！",
                "主人！看我看我！我接球超准的！");
        putTemplates("DOG_CLINGY_VISIT",
                "汪汪！有新朋友来啦！！好开心！！",
                "主人你看！有人来了！我可以去打招呼吗！",
                "好兴奋！！来客人了！！我要转圈圈！",
                "客人！！你好！！我是这家的狗狗！（疯狂摇尾巴）");

        // 傲娇狗（PROUD）
        putTemplates("DOG_PROUD_STEAL_SNACK",
                "汪！谁动我的零食了？让本汪查查！",
                "哼！小贼有种别跑！...（其实只是原地转圈）",
                "呵，偷我肉干？本汪记仇了！",
                "零食被偷了...本汪很不高兴。（低头趴下，但耳朵竖着）");
        putTemplates("DOG_PROUD_PLAY_MATCH",
                "想玩？行吧，看你这么诚恳的份上...",
                "这球...一般般吧...（尾巴摇成螺旋桨）",
                "陪你玩一下也不是不行...（疯狂暗示）",
                "哼，这球扔得不够远...（但已经冲出去了）");
        putTemplates("DOG_PROUD_VISIT",
                "哦，来人了啊...（高冷地点点头）",
                "新面孔...需要本汪批准才能进门哦～",
                "哼，来客人了也不先跟我说一声...",
                "嗯...有客人。（端坐，保持优雅）");

        // 贪吃狗（GLUTTON）
        putTemplates("DOG_GLUTTON_STEAL_SNACK",
                "汪汪汪汪！！我的零食！！全没了！！世界末日了！！",
                "啊啊啊！我的肉干！我的骨头！我的所有！！谁偷的！！",
                "哪个天杀的偷了我的零食！！我跟你不共戴天！！",
                "我的零食仓库！！被洗劫了！！汪呜呜呜！！");
        putTemplates("DOG_GLUTTON_PLAY_MATCH",
                "玩可以！但赢了要有零食奖励！",
                "球在哪零食就在哪！冲啊！",
                "快扔！赢了我要吃大餐！",
                "玩球玩球！玩完记得给我好吃的！");
        putTemplates("DOG_GLUTTON_VISIT",
                "客人来了！！带零食了吗！！（疯狂摇尾巴）",
                "好香！！客人身上有好吃的味道！！",
                "新朋友！你好！你有吃的吗！（星星眼）",
                "来客人了！！会给我带肉干吗！！（期待）");

        // 佛系狗（ZEN）
        putTemplates("DOG_ZEN_STEAL_SNACK",
                "汪...没了就没了吧...反正晚饭也不远了...",
                "零食如浮云...随它去吧...",
                "嗯...被偷了啊...哦...（继续趴着）",
                "肉干没了...那...睡觉吧...（打了个哈欠）");
        putTemplates("DOG_ZEN_PLAY_MATCH",
                "玩？...嗯...我趴着看你们玩就好...",
                "球在这...你去捡吧...我歇会...",
                "动啊...好累...下次吧...",
                "玩不动了...让我躺会儿...（四脚朝天）");
        putTemplates("DOG_ZEN_VISIT",
                "哦，来人了...（抬了下眼皮又趴下）",
                "客人...你好...我继续睡了...",
                "世界纷纷扰扰...我只想睡觉...",
                "嗯...有动静...算了...（翻了个身）");
    }

    // 辅助方法：添加模板
    private static void putTemplates(String key, String... messages) {
        TEMPLATES.put(key, Arrays.asList(messages));
    }

    /**
     * 获取随机消息
     * @param petType     宠物种类（Species.CAT 或 Species.DOG）
     * @param personality 性格（Personality.CLINGY / PROUD / GLUTTON / ZEN）
     * @param interaction 互动类型（InteractionType.STEAL_SNACK / PLAY_MATCH / VISIT）
     * @return 随机选取的消息，若无匹配则返回默认消息
     */
    public static String getRandomMessage(Species petType, String personality, SocialInteractionType interaction) {
        String key = petType.name() + "_" + Personality.nextString(personality) + "_" + interaction.name();
        List<String> messages = TEMPLATES.get(key);
        if (messages == null || messages.isEmpty()) {
            return "（宠物发出了可爱的声音）"; // fallback
        }
        int index = ThreadLocalRandom.current().nextInt(messages.size());
        return messages.get(index);
    }
}
