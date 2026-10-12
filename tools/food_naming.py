"""
USDA FoodData Central（SR Legacy）中文命名表。

为什么需要这个文件
------------------
USDA 里 `Cheese, gruyere` / `Oil, beluga, whale (Alaska Native)` 这种英文条目
直接进库，会让一个中文 App 的食物库变成「一半英文、还夹着鲸鱼油」的状态：
用户搜不到、看不懂，手动录入页更是没法看。

但 USDA 是公有领域的权威数据（每条都有 fdc_id 可回溯），
「牛油果 / 意大利面 / 切达奶酪」这类中式成分表里没有的食材，丢了很可惜。

所以这里做的是**人工审定**：
  * 只保留中国人日常真的会吃的条目（约 300 条）；
  * 每条给出简体中文名；
  * 匹配不上的、或者只匹配到「低脂/婴儿食品/阿拉斯加原住民」这类变体的，一律不要。

USDA 那 7,793 条里的大部分是「Beef, retail cuts, separable fat, raw」这样的
细分切法、品牌商品和婴儿食品，对一个记录热量的人来说没有价值，
**宁可没有，也不要一屏英文**。

匹配规则（见 build-food-db.py 的 pick_usda_row）：
  1. must 里的词必须全部出现在英文描述里（小写比较）；
  2. avoid 里的词一个都不能出现；
  3. 若某条描述与 must[0] 完全相等，优先取它（说明是标准条目而非变体）；
  4. 否则取最短的描述（越长说明修饰词越多，越可能是细分变体）。
"""

# 全局排除：任何条目只要带这些词就永远不要
GLOBAL_AVOID = (
    "alaska native",
    "babyfood",
    "baby food",
    "infant",
    "usda commodity",
    "dehydrated",
    "dry powder",
    "powdered",
)

# (简体中文名, 分类, 必须包含, 必须不含)
USDA_PICKS = [
    # ---------------- 乳制品 ----------------
    ("全脂牛奶", "乳制品", ("milk, whole",), ("chocolate", "dry", "canned")),
    ("低脂牛奶（2%）", "乳制品", ("milk, reduced fat, fluid, 2%",), ("chocolate",)),
    ("脱脂牛奶", "乳制品", ("milk, nonfat, fluid",), ("chocolate",)),
    ("切达奶酪", "乳制品", ("cheese, cheddar",),
     ("low", "fat free", "sharp", "shredded", "sliced", "diced", "reduced", "dry", "cold pack", "pasteurized")),
    # 不能取非脂/低脂变体：`Cheese, mozzarella, nonfat` 只有 141 kcal/100g，
    # 而正常的马苏里拉（全脂/半脱脂）是 299~300 —— 差一倍。
    ("马苏里拉奶酪", "乳制品", ("cheese, mozzarella",), ("low", "fat free", "reduced", "nonfat")),
    ("帕玛森奶酪", "乳制品", ("cheese, parmesan",), ("low", "fat free", "reduced")),
    ("奶油奶酪", "乳制品", ("cheese, cream",), ("low", "fat free", "reduced")),
    ("茅屋奶酪（白干酪）", "乳制品", ("cheese, cottage",), ("low", "fat free", "reduced", "dry")),
    ("蓝纹奶酪", "乳制品", ("cheese, blue",), ("low", "fat free", "reduced")),
    ("瑞士奶酪", "乳制品", ("cheese, swiss",), ("low", "fat free", "reduced")),
    ("希腊酸奶（原味无脂）", "乳制品", ("yogurt, greek, plain, nonfat",), ()),
    ("希腊酸奶（原味全脂）", "乳制品", ("yogurt, greek, plain, whole milk",), ()),
    ("原味酸奶（全脂）", "乳制品", ("yogurt, plain, whole milk",), ()),
    ("无脂酸奶", "乳制品", ("yogurt, plain, skim milk",), ()),
    ("淡奶油（重奶油）", "乳制品", ("cream, fluid, heavy whipping",), ()),
    ("黄油（含盐）", "乳制品", ("butter, salted",), ("whipped", "stick")),
    ("无盐黄油", "乳制品", ("butter, without salt",), ("whipped",)),
    ("人造黄油", "油脂", ("margarine, regular, hard",), ("low", "fat free", "tub")),

    # ---------------- 谷薯主食 ----------------
    ("白米饭（熟）", "谷薯", ("rice, white, long-grain, regular, cooked",), ()),
    ("糙米饭（熟）", "谷薯", ("rice, brown, long-grain, cooked",), ()),
    ("意大利面（熟）", "谷薯", ("pasta, cooked, enriched",), ("with added salt", "meat", "tomato")),
        ("全麦意面（熟）", "谷薯", ("pasta, whole-wheat, cooked",), ()),
    ("白面包", "谷薯", ("bread, white, commercially prepared",), ("toasted", "diet", "low")),
    ("全麦面包", "谷薯", ("bread, whole-wheat, commercially prepared",), ("toasted",)),
    ("贝果（原味）", "谷薯", ("bagels, plain",), ()),
    ("牛角包（可颂）", "谷薯", ("croissants, butter",), ()),
    ("英式松饼", "谷薯", ("english muffins",), ()),
    ("墨西哥薄饼（面粉）", "谷薯", ("tortillas, ready-to-bake or -fry, flour",), ()),
    ("蓝莓马芬", "谷薯", ("muffins, blueberry, commercially prepared",), ()),
    ("燕麦粥（熟）", "谷薯", ("cereals, oats, regular and quick",), ("instant", "fortified")),
    ("燕麦片（生）", "谷薯", ("cereals, oats, regular and quick, not fortified, dry",), ()),
    ("藜麦（熟）", "谷薯", ("quinoa, cooked",), ()),
    ("库斯库斯（熟）", "谷薯", ("couscous, cooked",), ()),
    ("大麦（熟）", "谷薯", ("barley, pearled, cooked",), ()),
    ("玉米面", "谷薯", ("cornmeal, degermed, unenriched, yellow",), ()),
    ("中筋面粉", "谷薯", ("wheat flour, white, all-purpose, enriched, bleached",), ("self-rising",)),
    ("全麦面粉", "谷薯", ("wheat flour, whole-grain",), ()),
    ("玉米片（早餐麦片）", "谷薯", ("cereals ready-to-eat, ralston corn flakes",), ()),

    # ---------------- 肉蛋 ----------------
    ("鸡蛋", "肉蛋", ("egg, whole, raw, fresh",), ("dried", "yolk", "white")),
    ("煮鸡蛋", "肉蛋", ("egg, whole, cooked, hard-boiled",), ()),
    ("煎鸡蛋", "肉蛋", ("egg, whole, cooked, fried",), ()),
    ("蛋清", "肉蛋", ("egg, white, raw, fresh",), ("dried",)),
    ("蛋黄", "肉蛋", ("egg, yolk, raw, fresh",), ("dried",)),
    ("鸡胸肉（熟，去皮）", "肉蛋", ("chicken, broilers or fryers, breast, meat only, cooked, roasted",), ()),
    ("鸡胸肉（生，去皮）", "肉蛋", ("chicken, broiler or fryers, breast, skinless, boneless, meat only, raw",), ()),
    ("鸡腿肉（熟，去皮）", "肉蛋", ("chicken, broilers or fryers, thigh, meat only, cooked, roasted",), ()),
    ("鸡腿（熟，带皮）", "肉蛋", ("chicken, broilers or fryers, drumstick, meat and skin, cooked",), ("fried",)),
    ("鸡翅（熟，带皮）", "肉蛋", ("chicken, broilers or fryers, wing, meat and skin, cooked",), ("fried",)),
    ("炸鸡（裹粉）", "肉蛋", ("chicken, broilers or fryers, breast, meat and skin, cooked, fried, batter",), ()),
    ("烤鸡（带皮）", "肉蛋", ("chicken, roasting, meat and skin",), ("fried",)),
    ("牛里脊（生）", "肉蛋", ("beef, tenderloin, steak, separable lean only", "all grades, raw"), ()),
    ("牛腩（熟，炖）", "肉蛋", ("beef, chuck, arm pot roast, separable lean only",), ("raw",)),
    ("牛肉馅（85% 瘦）", "肉蛋", ("beef, ground, 85% lean meat / 15% fat, raw",), ("cooked", "patty")),
    ("煎牛肉饼", "肉蛋", ("beef, ground, 85% lean meat / 15% fat, patty, cooked",), ()),
    ("西冷牛排（熟）", "肉蛋", ("beef, top sirloin, steak, separable lean only", "cooked, broiled"), ("raw",)),
    ("猪里脊（熟）", "肉蛋", ("pork, fresh, loin, tenderloin, separable lean only, cooked",), ()),
    ("猪肉（里脊，生）", "肉蛋", ("pork, fresh, loin, tenderloin, separable lean only, raw",), ()),
    ("五花肉（生）", "肉蛋", ("pork, fresh, belly, raw",), ()),
    ("培根（熟）", "肉蛋", ("pork, cured, bacon, cooked",), ("canadian",)),
    ("火腿（熟）", "肉蛋", ("ham, sliced, regular",), ("honey", "low", "canned")),
    # 意式香肠吃的是猪肉生肠煎/烤，`Sausage, Italian, sweet, links`（149）是另一类甜味低脂肠
    ("香肠（意式）", "肉蛋", ("sausage, italian, pork, mild, cooked",), ("sweet", "turkey", "chicken")),
    ("热狗肠", "肉蛋", ("frankfurter, beef",), ()),
    ("烤火鸡胸肉", "肉蛋", ("turkey, breast, from whole bird, meat only", "roasted"), ("deli", "smoked")),
    ("羊肉（腿，熟）", "肉蛋", ("lamb, leg, whole (shank and sirloin)",),
     ("raw", "australian", "new zealand", "lean only")),

    # ---------------- 水产 ----------------
    ("三文鱼（大西洋，熟）", "水产", ("fish, salmon, atlantic, farmed, cooked",), ("smoked", "canned", "coho", "chum", "pink", "sockeye")),
    ("三文鱼（大西洋，生）", "水产", ("fish, salmon, atlantic, farmed, raw",), ("smoked", "coho", "chum", "pink", "sockeye")),
    ("金枪鱼（水浸罐头）", "水产", ("fish, tuna, light, canned in water",), ("oil",)),
    ("金枪鱼（油浸罐头）", "水产", ("fish, tuna, light, canned in oil", "drained"), ()),
    ("鳕鱼（太平洋，生）", "水产", ("fish, cod, pacific, raw",), ()),
    ("罗非鱼（生）", "水产", ("fish, tilapia, raw",), ()),
    ("鳟鱼（生）", "水产", ("fish, trout, rainbow, farmed, raw",), ()),
    ("鲭鱼（生）", "水产", ("fish, mackerel, atlantic, raw",), ()),
    ("沙丁鱼（罐头）", "水产", ("fish, sardine, atlantic, canned in oil",), ()),
    ("巴沙鱼／罗非鱼片（生）", "水产", ("fish, bass, striped, raw",), ()),
    ("虾（熟）", "水产", ("crustaceans, shrimp, mixed species, cooked",), ("breaded", "canned", "imitation")),
    ("虾（生）", "水产", ("crustaceans, shrimp, mixed species, raw",), ("canned", "imitation")),
    ("蟹肉（罐头）", "水产", ("crustaceans, crab, blue, canned",), ()),
    ("龙虾（熟）", "水产", ("crustaceans, lobster, northern, cooked",), ()),
    ("生蚝（太平洋，生）", "水产", ("mollusks, oyster, pacific, raw",), ("canned", "cooked", "breaded", "fried")),
    ("蛤蜊（生）", "水产", ("mollusks, clam, mixed species, raw",), ("canned",)),
    ("扇贝（生）", "水产", ("mollusks, scallop, mixed species, raw",), ("breaded", "fried")),
    ("鱿鱼（生）", "水产", ("mollusks, squid, mixed species, raw",), ("fried",)),
    ("海带（生）", "水产", ("seaweed, kelp, raw",), ()),
    ("螺旋藻（干）", "水产", ("seaweed, spirulina, dried",), ()),

    # ---------------- 蔬菜 ----------------
    ("西兰花", "蔬菜", ("broccoli, raw",), ()),
    ("菠菜", "蔬菜", ("spinach, raw",), ()),
    ("生菜（罗马）", "蔬菜", ("lettuce, cos or romaine, raw",), ()),
    ("球生菜", "蔬菜", ("lettuce, iceberg",), ()),
    ("番茄", "蔬菜", ("tomatoes, red, ripe, raw",), ("canned", "juice", "sun-dried")),
    ("黄瓜（带皮）", "蔬菜", ("cucumber, with peel, raw",), ()),
    ("胡萝卜", "蔬菜", ("carrots, raw",), ()),
    ("洋葱", "蔬菜", ("onions, raw",), ("dehydrated", "spring", "welsh")),
    ("大蒜", "蔬菜", ("garlic, raw",), ()),
    ("红甜椒", "蔬菜", ("peppers, sweet, red, raw",), ()),
    ("绿甜椒", "蔬菜", ("peppers, sweet, green, raw",), ()),
    ("白蘑菇", "蔬菜", ("mushrooms, white, raw",), ("canned",)),
    ("香菇（熟）", "蔬菜", ("mushrooms, shiitake, cooked",), ("dried",)),
    ("甜玉米", "蔬菜", ("corn, sweet, yellow, raw",), ("canned", "frozen")),
    ("青豆", "蔬菜", ("peas, green, raw",), ("canned", "frozen", "split")),
    ("四季豆", "蔬菜", ("beans, snap, green, raw",), ("canned", "frozen")),
    ("卷心菜", "蔬菜", ("cabbage, raw",), ("chinese", "red", "savoy")),
    ("大白菜（中国）", "蔬菜", ("cabbage, chinese (pe-tsai), raw",), ()),
    ("菜心／小白菜", "蔬菜", ("cabbage, chinese (pak-choi), raw",), ()),
    ("花椰菜", "蔬菜", ("cauliflower, raw",), ("frozen",)),
    ("茄子", "蔬菜", ("eggplant, raw",), ()),
    ("西葫芦", "蔬菜", ("squash, summer, zucchini",), ()),
    ("南瓜", "蔬菜", ("pumpkin, raw",), ()),
    ("芹菜", "蔬菜", ("celery, raw",), ()),
    ("芦笋", "蔬菜", ("asparagus, raw",), ()),
    ("土豆（带皮）", "蔬菜", ("potatoes, flesh and skin, raw",), ("frozen", "hash", "french", "canned")),
    ("红薯（生）", "蔬菜", ("sweet potato, raw",), ("frozen", "canned", "leaves")),
    ("羽衣甘蓝", "蔬菜", ("kale, raw",), ("frozen", "canned", "cooked")),
    ("姜", "蔬菜", ("ginger root, raw",), ()),
    ("秋葵", "蔬菜", ("okra, raw",), ("frozen",)),
    ("笋瓜（butternut）", "蔬菜", ("squash, winter, butternut",), ()),
    ("甜菜", "蔬菜", ("beets, raw",), ("canned",)),
    ("萝卜（白）", "蔬菜", ("radishes, raw",), ()),
    ("豆芽（绿豆）", "蔬菜", ("mung beans, mature seeds, sprouted, raw",), ("canned", "boiled")),
    ("苜蓿芽", "蔬菜", ("alfalfa seeds, sprouted, raw",), ()),

    # ---------------- 水果 ----------------
    ("苹果（带皮）", "水果", ("apples, raw, with skin",), ()),
    ("苹果（去皮）", "水果", ("apples, raw, without skin",), ()),
    ("香蕉", "水果", ("bananas, raw",), ()),
    ("橙子", "水果", ("oranges, raw, all commercial varieties",), ()),
    ("葡萄", "水果", ("grapes, red or green",), ()),
    ("草莓", "水果", ("strawberries, raw",), ("frozen",)),
    ("西瓜", "水果", ("watermelon, raw",), ()),
    ("梨", "水果", ("pears, raw",), ("canned", "dried")),
    ("桃子", "水果", ("peaches, yellow, raw",), ("canned", "dried", "frozen")),
    ("芒果", "水果", ("mangos, raw",), ()),
    ("猕猴桃", "水果", ("kiwifruit, green, raw",), ()),
    ("蓝莓", "水果", ("blueberries, raw",), ("frozen", "canned")),
    ("树莓", "水果", ("raspberries, raw",), ("frozen", "canned")),
    ("樱桃", "水果", ("cherries, sweet, raw",), ("canned", "frozen", "sour")),
    ("柠檬", "水果", ("lemons, raw, without peel",), ()),
    ("菠萝", "水果", ("pineapple, raw",), ("canned", "juice")),
    ("哈密瓜", "水果", ("cantaloupe, raw",), ()),
    ("李子", "水果", ("plums, raw",), ("dried",)),
    # 同样要排掉地区变体（佛州西柚 30 vs 通用 42）
    ("西柚", "水果", ("grapefruit, raw, pink and red",), ("canned", "juice", "florida", "california")),
    ("木瓜", "水果", ("papayas, raw",), ()),
    ("石榴", "水果", ("pomegranates, raw",), ()),
    ("无花果（生）", "水果", ("figs, raw",), ("dried", "canned")),
    ("蔓越莓（生）", "水果", ("cranberries, raw",), ("dried", "juice")),
    ("油桃", "水果", ("nectarines, raw",), ()),
    # 必须排掉地区变体：`Avocados, raw, Florida`（佛州品种，脂肪低）只有 120 kcal/100g，
    # 而加州/通用商业品种是 160~167。取「描述最短」会误取到佛州种，差 25%。
    ("牛油果", "水果", ("avocados, raw",), ("florida", "california")),
    ("葡萄干（无核）", "水果", ("raisins, golden, seedless",), ()),
    ("枣（椰枣）", "水果", ("dates, medjool",), ()),
    ("西梅干", "水果", ("plums, dried (prunes)",), ("stewed", "canned", "juice")),

    # ---------------- 豆类 ----------------
    ("黄豆（干）", "豆类", ("soybeans, mature seeds, raw",), ("sprouted", "green")),
    ("毛豆（熟）", "豆类", ("soybeans, green, cooked",), ()),
    ("豆腐（北豆腐／老豆腐）", "豆类", ("tofu, raw, firm",), ("fried", "salted", "yogurt", "okara")),
    ("豆腐（嫩豆腐）", "豆类", ("tofu, raw, regular",), ("fried", "salted", "yogurt", "soft")),
    ("豆浆（原味）", "豆类", ("soymilk, original and vanilla",), ("light", "chocolate")),
    ("鹰嘴豆（熟）", "豆类", ("chickpeas (garbanzo beans, bengal gram), mature seeds, cooked",), ()),
    ("红腰豆（熟）", "豆类", ("beans, kidney, red, mature seeds, cooked",), ()),
    ("黑豆（熟）", "豆类", ("beans, black, mature seeds, cooked",), ()),
    ("扁豆（熟）", "豆类", ("lentils, mature seeds, cooked",), ()),
    ("绿豆（熟）", "豆类", ("mung beans, mature seeds, cooked",), ("sprouted",)),
    ("青豆（熟）", "豆类", ("peas, split, mature seeds, cooked",), ()),
    ("红豆（熟）", "豆类", ("beans, adzuki, mature seed, cooked",), ()),

    # ---------------- 坚果种子 ----------------
    # 中文里「杏仁」指杏核仁，USDA 这条是巴旦木（almond），两者不是同一种东西，
    # 名字必须区分开，否则会和成分表里的「杏仁」撞名。
    ("巴旦木（大杏仁）", "坚果", ("nuts, almonds",), ("oil", "butter", "blanched")),
    ("核桃", "坚果", ("nuts, walnuts, english",), ("oil",)),
    ("腰果", "坚果", ("nuts, cashew nuts, raw",), ("oil", "butter")),
    ("开心果", "坚果", ("nuts, pistachio nuts, raw",), ()),
    ("花生（生）", "坚果", ("peanuts, all types, raw",), ("oil", "butter")),
    ("熟花生（烤）", "坚果", ("peanuts, all types, dry-roasted, with salt",), ()),
    ("花生酱", "坚果", ("peanut butter, smooth style, with salt",), ()),
    ("榛子", "坚果", ("nuts, hazelnuts or filberts",), ("oil",)),
    ("碧根果", "坚果", ("nuts, pecans",), ("oil",)),
    ("夏威夷果", "坚果", ("nuts, macadamia nuts, raw",), ("oil",)),
    ("松子", "坚果", ("nuts, pine nuts, dried",), ()),
    ("栗子（熟）", "坚果", ("chestnuts, european, roasted",), ()),
    ("奇亚籽", "坚果", ("chia seeds, dried",), ()),
    ("亚麻籽", "坚果", ("seeds, flaxseed",), ("oil",)),
    ("葵花籽仁", "坚果", ("seeds, sunflower seed kernels, dried",), ()),
    ("南瓜籽仁", "坚果", ("seeds, pumpkin and squash seed kernels, roasted",), ()),
    ("芝麻（熟）", "坚果", ("seeds, sesame seeds, whole, roasted and toasted",), ()),

    # ---------------- 调味料 ----------------
    # 中国食物成分表这份公开数据里没有调味品分类，而家常菜离不开酱油和醋：
    # 10~15g 酱油约 8 kcal，量虽小但直接关系到「配比能不能复算」，所以补上。
    ("酱油（生抽）", "调味", ("soy sauce made from soy and wheat (shoyu)",), ("low sodium", "reduced")),
    ("白醋", "调味", ("vinegar, distilled",), ()),

    # ---------------- 油脂 ----------------
    ("橄榄油", "油脂", ("oil, olive, salad or cooking",), ()),
    ("菜籽油", "油脂", ("oil, canola",), ()),
    ("大豆油", "油脂", ("oil, soybean, salad or cooking",), ()),
    ("葵花籽油", "油脂", ("oil, sunflower, linoleic",), ()),
    ("椰子油", "油脂", ("oil, coconut",), ()),
    ("芝麻油", "油脂", ("oil, sesame, salad or cooking",), ()),
    ("亚麻籽油", "油脂", ("oil, flaxseed",), ()),
    ("玉米油", "油脂", ("oil, corn, industrial and retail",), ()),
    ("猪油", "油脂", ("lard",), ()),
    ("起酥油", "油脂", ("shortening, vegetable",), ("frying",)),

    # ---------------- 零食 ----------------
    ("薯片（原味）", "零食", ("snacks, potato chips, plain, salted",), ()),
    ("低脂薯片", "零食", ("snacks, potato chips, fat free",), ()),
    ("爆米花（无油）", "零食", ("snacks, popcorn, air-popped",), ()),
    ("爆米花（黄油味）", "零食", ("snacks, popcorn, oil-popped",), ()),
    ("椒盐卷饼", "零食", ("snacks, pretzels, hard, plain, salted",), ()),
    ("玉米片（零食）", "零食", ("snacks, tortilla chips, plain",), ()),
    ("混合坚果", "零食", ("nuts, mixed nuts, dry roasted, with peanuts, without salt added",), ()),
    ("燕麦棒／能量棒", "零食", ("snacks, granola bar",), ()),
    ("牛奶巧克力", "零食", ("candies, milk chocolate",), ("covered", "with almonds", "bar")),
    ("黑巧克力（70-85%）", "零食", ("chocolate, dark, 70-85% cacao",), ()),
    ("曲奇饼干（巧克力豆）", "零食", ("cookies, chocolate chip",), ("dough", "refrigerated")),
    ("威化饼干", "零食", ("cookies, vanilla wafers",), ()),
    ("苏打饼干", "零食", ("crackers, saltines",), ()),
    ("全麦饼干", "零食", ("crackers, whole-wheat",), ()),
    ("甜甜圈", "零食", ("doughnuts, cake-type, plain",), ("glazed",)),
    ("巧克力蛋糕（含糖霜）", "零食", ("cake, chocolate, commercially prepared with chocolate frosting",), ()),
    ("芝士蛋糕", "零食", ("cake, cheesecake",), ()),
    ("苹果派", "零食", ("pie, apple, commercially prepared",), ("dutch",)),
    ("布朗尼", "零食", ("brownies, commercially prepared",), ()),
    ("香草冰淇淋", "零食", ("ice creams, vanilla",), ("light", "fat free", "french")),
    ("巧克力冰淇淋", "零食", ("ice creams, chocolate",), ("light", "fat free", "rich")),
    ("蜂蜜", "零食", ("honey",), ("comb",)),
    ("白砂糖", "零食", ("sugars, granulated",), ()),
    ("果酱", "零食", ("jams and preserves",), ("low", "sugar")),
    ("蛋黄酱", "零食", ("salad dressing, mayonnaise, regular",), ("light", "low", "fat free")),
    ("番茄酱（调味）", "零食", ("catsup",), ("low sodium",)),

    # ---------------- 饮料 ----------------
    ("可乐", "饮料", ("beverages, carbonated, cola, regular",), ("fast-food",)),
    ("速食店可乐", "饮料", ("beverages, carbonated, cola, fast-food cola",), ()),
    ("柠檬味汽水（雪碧）", "饮料", ("beverages, carbonated, lemon-lime soda, no caffeine",), ()),
    ("姜汁汽水", "饮料", ("beverages, carbonated, ginger ale",), ()),
    ("橙味汽水", "饮料", ("beverages, carbonated, orange",), ("low", "breakfast")),
    ("咖啡（现煮）", "饮料", ("beverages, coffee, brewed, prepared with tap water",), ("instant", "decaffeinated", "espresso")),
    ("速溶咖啡（冲好）", "饮料", ("beverages, coffee, instant, regular, prepared with water",), ()),
    ("红茶（冲泡）", "饮料", ("beverages, tea, black, brewed",), ("decaffeinated", "instant")),
    ("绿茶（冲泡）", "饮料", ("beverages, tea, green, brewed",), ("decaffeinated",)),
    ("鲜榨橙汁", "饮料", ("orange juice, raw",), ()),
    ("苹果汁（罐装）", "饮料", ("apple juice, canned or bottled, unsweetened",), ()),
    ("葡萄汁", "饮料", ("grape juice, canned or bottled, unsweetened",), ()),
    ("番茄汁", "饮料", ("tomato juice, canned",), ("low sodium", "cocktail")),
    ("西柚汁（白）", "饮料", ("grapefruit juice, white, canned or bottled, unsweetened",), ()),
    ("蔓越莓汁", "饮料", ("cranberry juice, unsweetened",), ("cocktail",)),
    ("啤酒", "饮料", ("alcoholic beverage, beer, regular, all",), ()),
    ("淡啤酒", "饮料", ("alcoholic beverage, beer, light",), ("budweiser", "michelob")),
    ("红葡萄酒", "饮料", ("alcoholic beverage, wine, table, red",), ("non-alcoholic",)),
    ("白葡萄酒", "饮料", ("alcoholic beverage, wine, table, white",), ("non-alcoholic", "sparkling")),
    ("清酒", "饮料", ("alcoholic beverage, rice (sake)",), ()),
    ("运动饮料（瓶装）", "饮料", ("gatorade, g performance",), ()),
    ("能量饮料", "饮料", ("beverages, energy drink",), ()),
    # 不收录「柠檬水」：USDA 只有粉冲/浓缩的 pink lemonade（43）和 low 版本（21），
    # 与中国人说的「柠檬水」不是同一个东西，与其给个含糊数，不如不给。
    ("椰子水", "饮料", ("beverages, coconut water",), ()),
    ("热巧克力（冲好）", "饮料", ("beverages, cocoa mix, powder, prepared with water",), ("no sugar",)),
    ("米浆／米汤饮品", "饮料", ("beverages, rice milk",), ()),

    # ---------------- 快餐／西式 ----------------
    ("芝士汉堡", "快餐", ("fast foods, cheeseburger",), ("double", "triple", "large")),
    ("汉堡包", "快餐", ("fast foods, hamburger",), ("double", "triple", "large", "cheese")),
    ("炸薯条", "快餐", ("fast foods, potato, french fried",), ("with cheese", "chili")),
    ("披萨（奶酪，常规饼底）", "快餐", ("pizza, cheese topping, regular crust, frozen, cooked",), ()),
    ("意式香肠披萨", "快餐", ("pizza, pepperoni",), ("thin", "frozen", "rising")),
    ("鸡肉三明治", "快餐", ("fast foods, chicken fillet sandwich",), ("grilled",)),
    ("鱼肉三明治（配塔塔酱）", "快餐", ("fast foods, fish sandwich, with tartar sauce",), ("cheese",)),
    ("炸鸡块", "快餐", ("fast foods, chicken, breaded and fried",), ("nuggets, dark", "tenders")),
    ("洋葱圈", "快餐", ("onion rings, breaded",), ("frozen",)),
    ("玉米卷（脆）", "快餐", ("fast foods, taco",), ("salad", "soft")),
    ("卷饼（burrito）", "快餐", ("fast foods, burrito",), ("breakfast",)),
    ("玉米片配芝士（纳乔）", "快餐", ("fast foods, nachos, with cheese",), ("beans",)),
    ("潜艇三明治", "快餐", ("fast foods, submarine sandwich",),
     ("tuna", "roast beef", "turkey", "meatball", "bacon", "steak")),
    ("鸡蛋三明治", "快餐", ("fast foods, egg, scrambled",), ()),
    ("早餐卷饼", "快餐", ("fast foods, breakfast burrito",), ()),
    ("薄煎饼（pancake）", "快餐", ("pancakes, plain, dry mix, complete, prepared",), ("buttermilk",)),
    ("华夫饼", "快餐", ("waffles, plain, frozen, ready-to-heat",), ("buttermilk", "toasted")),
    ("冰淇淋圣代（巧克力）", "快餐", ("fast foods, sundae, hot fudge",), ()),
    ("奶昔（巧克力）", "快餐", ("milk shakes, thick chocolate",), ()),
]


# 额外中文别名：只影响搜索，不影响显示名、更不影响数值。
# 加的是「用户会打、但和条目名不一致」的日常叫法 ——
# 库里叫「红葡萄酒」，用户搜「红酒」搜不到，这条数据对他就是不存在。
EXTRA_ZH_ALIAS = {
    "红葡萄酒": "红酒",
    "意大利面（熟）": "意面,意粉",
    "全麦意面（熟）": "意面,全麦意粉",
    "切达奶酪": "芝士",
    "帕玛森奶酪": "芝士粉",
    "马苏里拉奶酪": "披萨芝士",
    "香草冰淇淋": "冰淇淋,雪糕",
    "巧克力冰淇淋": "冰淇淋,雪糕",
    "白面包": "吐司",
    "全麦面包": "吐司",
    "可乐": "汽水",
    "速食店可乐": "汽水",
    "炸薯条": "薯条",
    "牛油果": "鳄梨",
    "燕麦片（生）": "燕麦,麦片",
    "燕麦粥（熟）": "麦片粥",
    "白米饭（熟）": "大米饭,米饭",
    "糙米饭（熟）": "糙米,米饭",
    "鸡蛋": "全蛋,土鸡蛋",
    "煮鸡蛋": "水煮蛋,白煮蛋",
    "豆浆": "豆奶",
}


def match_report(missing):
    """把没匹配上的条目打印出来，供人工修正（不静默丢数据）。"""
    if not missing:
        return "所有中文命名都匹配到了 USDA 条目"
    return "未匹配（需要修正 must/avoid 或删除）:\n  " + "\n  ".join(missing)
