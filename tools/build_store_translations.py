import csv

DATA = [
    {
        "code": "en-US",
        "lang": "English (United States)",
        "short": "Plan, sneak, and parkour your way through dangerous heists",
        "intro": "Infiltrate: Shadow Heist is a 2D stealth-action game about precision, movement, and getting in without being seen.",
        "story": "Under cover of darkness, you infiltrate a heavily guarded industrial shipyard on a high-stakes heist. The objective: locate Container 17 and extract what lies within. But as you slip past the perimeter, the mission changes—Container 17 has been moved, security is on high alert, and a shadowy trail pulls you deep into restricted underground tunnels.\n\nThere, locked within the holding cells, you discover a mysterious prisoner who shares your past.\n\nBreak them out, navigate lethal security grids, and execute an impossible escape.",
        "headers": [
            "Master the art of infiltration",
            "Move carefully, time your actions, hide from guards, and use the environment to create your own path.",
            "Parkour through dangerous locations",
            "Jump, crouch, climb, swing, and move through challenging environments built around realistic objects and obstacles.",
            "Solve environmental puzzles",
            "Use cargo equipment, containers, lifts, and barriers to open routes and overcome obstacles.",
            "Stay unseen",
            "Guards are always watching. One mistake can expose you and force you to try again.",
            "Follow the mystery",
            "Every mission reveals another piece of the story as you track Container 17, venture beneath the yard, and fight to escape together.",
            "Infiltrate. Move. Discover the truth."
        ]
    },
    {
        "code": "ar",
        "lang": "Arabic",
        "short": "خطط وتسلل ومارس الباركور في عمليات سطو محفوفة بالمخاطر",
        "intro": "Infiltrate: Shadow Heist هي لعبة تسلل وحركة ثنائية الأبعاد تعتمد على الدقة وحسن الحركة والدخول دون أن يراك أحد.",
        "story": "تحت جنح الظلام، تتسلل إلى حوض سفن صناعي يخضع لحراسة مشددة في عملية سطو خطيرة. الهدف: تحديد موقع الحاوية 17 واستخراج ما بداخلها. ولكن بمجرد اختراق السياج، تتغير المهمة—تم نقل الحاوية، والأمن في حالة تأهب قصوى، وأثر غامض يقودك إلى عمق الأنفاق المحظورة تحت الأرض.\n\nهناك، داخل زنزانة احتجاز، تكتشف سجينًا غامضًا يشترك معك في ماضيك.\n\nحرره، وتفادَ شبكات الأمان القاتلة، ونفّذ هروبًا شبه مستحيل.",
        "headers": [
            "أتقن فن التسلل",
            "تحرك بحذر، واضبط توقيت حركاتك، واختبئ من الحراس، واستغل البيئة المحيطة لتشق طريقك الخاص.",
            "مارس الباركور عبر مواقع خطرة",
            "اقفز، وانحنِ، وتسلق، وتأرجح، واعبر بيئات مليئة بالعقبات الواقعية.",
            "حل ألغاز البيئة التفاعلية",
            "استخدم معدات الشحن، والحاويات، والمصاعد، والحواجز لفتح مسارات جديدة.",
            "ابقَ بعيدًا عن الأنظار",
            "الحراس يراقبون دائمًا. خطأ واحد كفيل بكشفك وإجبارك على المحاولة من جديد.",
            "تتبع خيوط اللغز",
            "تكشف كل مهمة فصلاً جديداً من القصة بينما تتعقب الحاوية 17 وتناضل للهروب معاً.",
            "تسلل. تحرك. اكتشف الحقيقة."
        ]
    },
    {
        "code": "zh-HK",
        "lang": "Chinese (Hong Kong)",
        "short": "精心策劃、隱秘潛行，在險境重重的劫案中穿梭跑酷",
        "intro": "《Infiltrate: Shadow Heist》是一款關於精準操作、靈活走位以及在無人察覺下完美潛入的2D潛行動作遊戲。",
        "story": "夜色漸濃，你隻身潛入重兵把守的工業造船廠，執行一場高風險的重大劫案。目標：鎖定17號貨櫃並奪取其中機密。然而剛突破防線，任務即遭生變——貨櫃已被移走，全區戒備拉至最高，一道神秘足跡將你引領至幽深的地下禁閉通道。\n\n在冰冷的牢房深處，你赫然發現一位與你的過去有著密切關聯的神秘囚犯。\n\n營救同伴，突破重重致命防禦雷射，共同執行這場看似不可能的逃脫。",
        "headers": [
            "掌握潛行精髓",
            "謹慎走位、抓準時機、躲避警衛視線，善用周圍環境開闢生路。",
            "在險境中跑酷飛躍",
            "跳躍、下蹲、攀爬、繩索擺盪，克服由逼真障礙物構成的嚴苛地形挑戰。",
            "破解場景機關謎題",
            "操作貨運設備、集裝箱、升降平台與安全閘門，開拓突破路徑。",
            "絕對匿蹤潛行",
            "守衛隨時保持警惕。任何微小失誤都將暴露行蹤，迫使你重整旗鼓。",
            "追尋背後謎團",
            "隨著追蹤17號貨櫃與深入地下基地，每個任務都將拼湊出震撼真相。",
            "潛入。行動。揭開真相。"
        ]
    },
    {
        "code": "zh-CN",
        "lang": "Chinese (Simplified)",
        "short": "精心策划、隐秘潜行，在险象环生的劫案中飞檐走壁",
        "intro": "《Infiltrate: Shadow Heist》是一款注重精准操作、敏捷走位与暗中潜入的2D潜行动作游戏。",
        "story": "夜幕降临，你潜入戒备森严的工业造船厂，执行一项高风险的重大劫案。目标：锁定17号集装箱并提取其中的机密。然而刚突破外围防线，任务便突生变故——集装箱已被转移，安保级别拉至最高，一条神秘踪迹引诱你深入地下禁区隧道。\n\n在幽暗的牢房深处，你发现了一名神秘的囚犯，他与你的过往有着千丝万缕的联系。\n\n将他营救出来，穿透致命的安保激光网，完成一场看似不可能的惊天大逃亡。",
        "headers": [
            "掌握潜行艺术",
            "小心前行，把控时机，躲避守卫视野，利用周围环境开辟属于你的潜入路线。",
            "在危险地带飞檐走壁",
            "奔跑、下蹲、攀爬、绳索摆荡，在充满写实障碍的复杂环境中挑战极限跑酷。",
            "破解场景机关谜题",
            "操控货运设备、集装箱、升降货梯与升降隔离栏，开启前进通道。",
            "绝对隐匿无声",
            "守卫时刻保持警惕。任何微小的失误都可能暴露行踪，迫使你重头再来。",
            "探寻未解之谜",
            "随着追踪17号集装箱并深入地下船厂，每个任务都将揭示一段扣人心弦的真相。",
            "潜入。行动。探寻真相。"
        ]
    },
    {
        "code": "zh-TW",
        "lang": "Chinese (Traditional)",
        "short": "精心策劃、隱秘潛行，在險境重重的劫案中穿梭跑酷",
        "intro": "《Infiltrate: Shadow Heist》是一款關於精準操作、靈活走位以及在無人察覺下完美潛入的2D潛行動作遊戲。",
        "story": "夜色漸濃，你隻身潛入重兵把守的工業造船廠，執行一場高風險的重大劫案。目標：鎖定17號貨櫃並奪取其中機密。然而剛突破防線，任務即遭生變——貨櫃已被移走，全區戒備拉至最高，一道神秘足跡將你引領至幽深的地下禁閉通道。\n\n在冰冷的牢房深處，你赫然發現一位與你的過去有著密切關聯的神秘囚犯。\n\n營救同伴，突破重重致命防禦雷射，共同執行這場看似不可能的逃脫。",
        "headers": [
            "掌握潛行精髓",
            "謹慎走位、抓準時機、躲避警衛視線，善用周圍環境開闢生路。",
            "在險境中跑酷飛躍",
            "跳躍、下蹲、攀爬、繩索擺盪，克服由逼真障礙物構成的嚴苛地形挑戰。",
            "破解場景機關謎題",
            "操作貨運設備、集裝箱、升降平台與安全閘門，開拓突破路徑。",
            "絕對匿蹤潛行",
            "守衛隨時保持警惕。任何微小失誤都將暴露行蹤，迫使你重整旗鼓。",
            "追尋背後謎團",
            "隨著追蹤17號貨櫃與深入地下基地，每個任務都將拼湊出震撼真相。",
            "潛入。行動。揭開真相。"
        ]
    },
    {
        "code": "fr-CA",
        "lang": "French (Canada)",
        "short": "Planifiez, infiltrez et sautez à travers des casses périlleux",
        "intro": "Infiltrate: Shadow Heist est un jeu d'action et d'infiltration en 2D axé sur la précision, le mouvement et la discrétion absolue.",
        "story": "Sous le couvert de l'obscurité, vous infiltrez un chantier naval industriel lourdement gardé pour un casse à haut risque. L'objectif : localiser le conteneur 17 et récupérer son contenu. Mais une fois le périmètre franchi, la mission tourne court : le conteneur a été déplacé, la sécurité est en alerte maximale et une piste obscure vous entraîne dans des tunnels souterrains restreints.\n\nLà, enfermé dans les cellules de détention, vous découvrez un mystérieux prisonnier qui partage votre passé.\n\nLibérez-le, déjouez les grilles de sécurité mortelles et réussissez une évasion impossible.",
        "headers": [
            "Maîtrisez l'art de l'infiltration",
            "Déplacez-vous prudemment, anticipez vos actions, cachez-vous des gardes et utilisez l'environnement pour tracer votre chemin.",
            "Parkour dans des zones périlleuses",
            "Sautez, accroupissez-vous, grimpez, balancez-vous et évoluez à travers des environnements exigeants remplis d'obstacles réalistes.",
            "Résolvez des énigmes environnementales",
            "Utilisez des équipements de fret, des conteneurs, des monte-charges et des barrières pour ouvrir des voies.",
            "Restez invisible",
            "Les gardes surveillent en permanence. Une seule erreur peut vous trahir et vous forcer à recommencer.",
            "Suivez le mystère",
            "Chaque mission révèle un nouvel élément de l'histoire alors que vous traquez le conteneur 17 et luttez pour vous échapper ensemble.",
            "Infiltrez. Progressez. Découvrez la vérité."
        ]
    },
    {
        "code": "fr-FR",
        "lang": "French (France)",
        "short": "Planifiez, infiltrez et sautez à travers des casses périlleux",
        "intro": "Infiltrate: Shadow Heist est un jeu d'action et d'infiltration en 2D axé sur la précision, le mouvement et la discrétion absolue.",
        "story": "Sous le couvert de l'obscurité, vous infiltrez un chantier naval industriel lourdement gardé pour un casse à haut risque. L'objectif : localiser le conteneur 17 et récupérer son contenu. Mais une fois le périmètre franchi, la mission tourne court : le conteneur a été déplacé, la sécurité est en alerte maximale et une piste obscure vous entraîne dans des tunnels souterrains restreints.\n\nLà, enfermé dans les cellules de détention, vous découvrez un mystérieux prisonnier qui partage votre passé.\n\nLibérez-le, déjouez les grilles de sécurité mortelles et réussissez une évasion impossible.",
        "headers": [
            "Maîtrisez l'art de l'infiltration",
            "Déplacez-vous prudemment, anticipez vos actions, cachez-vous des gardes et utilisez l'environnement pour tracer votre chemin.",
            "Parkour dans des zones périlleuses",
            "Sautez, accroupissez-vous, grimpez, balancez-vous et évoluez à travers des environnements exigeants remplis d'obstacles réalistes.",
            "Résolvez des énigmes environnementales",
            "Utilisez des équipements de fret, des conteneurs, des monte-charges et des barrières pour ouvrir des voies.",
            "Restez invisible",
            "Les gardes surveillent en permanence. Une seule erreur peut vous trahir et vous forcer à recommencer.",
            "Suivez le mystère",
            "Chaque mission révèle un nouvel élément de l'histoire alors que vous traquez le conteneur 17 et luttez pour vous échapper ensemble.",
            "Infiltrez. Progressez. Découvrez la vérité."
        ]
    },
    {
        "code": "de-DE",
        "lang": "German",
        "short": "Plane, schleiche und klettere durch gefährliche Raubzüge",
        "intro": "Infiltrate: Shadow Heist ist ein 2D-Stealth-Actionspiel, bei dem es um Präzision, Bewegung und unbemerktes Eindringen geht.",
        "story": "Im Schutz der Dunkelheit infiltrierst du eine schwer bewachte Industrie-Schiffswerft bei einem riskanten Raubzug. Das Ziel: Finde Container 17 und sichere seinen Inhalt. Doch kaum hast du das Gelände betreten, ändert sich die Mission: Container 17 wurde versetzt, die Sicherheitsstufe ist maximal und eine finstere Spur führt dich tief in unterirdische Wartungstunnel.\n\nDort, eingesperrt in einer Zelle, entdeckst du einen geheimnisvollen Gefangenen, der deine Vergangenheit teilt.\n\nBefreie ihn, meide tödliche Lasergitter und meistere eine scheinbar unmögliche Flucht.",
        "headers": [
            "Meistere die Kunst der Infiltration",
            "Bewege dich vorsichtig, passe dein Timing ab, verstecke dich vor Wachen und nutze die Umgebung.",
            "Parkour durch gefährliche Gebiete",
            "Springe, krieche, klettere, schwinge dich und meistere herausfordernde Areale voller realistischer Hindernisse.",
            "Löse Umgebungsrätsel",
            "Nutze Frachtausrüstung, Container, Lastenaufzüge und Schranken, um dir Wege zu bahnen.",
            "Bleibe ungesehen",
            "Die Wachen haben alles im Blick. Ein einziger Fehler verrät dich und zwingt dich zum Neustart.",
            "Folge dem Geheimnis",
            "Jede Mission enthüllt ein weiteres Puzzleteil, während du Container 17 nachspürst und ihr gemeinsam um die Flucht kämpft.",
            "Infiltrieren. Bewegen. Die Wahrheit enthüllen."
        ]
    },
    {
        "code": "hi-IN",
        "lang": "Hindi",
        "short": "खतरनाक डकैतियों में योजना बनाएं, चुपके से जाएं और पार्कौर करें",
        "intro": "Infiltrate: Shadow Heist सटीकता, सजग गतिविधियों और बिना दिखे घुसपैठ करने पर आधारित एक 2D स्टील्थ-एक्शन गेम है।",
        "story": "अंधेरे की आड़ में, आप एक भारी सुरक्षा वाले औद्योगिक शिपयार्ड में एक बड़े जोखिम भरे मिशन पर घुसपैठ करते हैं। उद्देश्य: कंटेनर 17 का पता लगाना और उसके अंदर की सामग्री को निकालना। लेकिन जैसे ही आप परिधि पार करते हैं, मिशन बदल जाता है—कंटेनर को स्थानांतरित कर दिया गया है, सुरक्षा हाई अलर्ट पर है, और एक रहस्यमयी सुराग आपको भूमिगत सुरंगों में खींच ले जाता है।\n\nवहां, बंदी गृह में, आपको एक रहस्यमयी कैदी मिलता है जिसका अतीत आपसे जुड़ा है।\n\nउसे छुड़ाएं, घातक सुरक्षा ग्रिड से बचें, और एक असंभव पलायन को अंजाम दें।",
        "headers": [
            "घुसपैठ की कला में महारत हासिल करें",
            "सावधानी से आगे बढ़ें, अपने कार्यों का समय तय करें, गार्डों से छिपें और अपना रास्ता खुद बनाने के लिए वातावरण का उपयोग करें।",
            "खतरनाक स्थानों पर पार्कौर करें",
            "कूदें, झुकें, चढ़ें, झूलें और वास्तविक बाधाओं से भरी चुनौतीपूर्ण परिस्थितियों से गुजरें।",
            "पर्यावरणीय पहेलियाँ सुलझाएँ",
            "रास्ते खोलने और बाधाओं को पार करने के लिए कार्गो उपकरण, कंटेनर, लिफ्ट और बैरियर का उपयोग करें।",
            "अनदेखे रहें",
            "गार्ड हमेशा निगरानी में रहते हैं। एक गलती आपको पकड़वा सकती है।",
            "रहस्य का पीछा करें",
            "प्रत्येक मिशन कहानी का एक और हिस्सा उजागर करता है जब आप कंटेनर 17 को ट्रैक करते हैं और एक साथ भागने के लिए लड़ते हैं।",
            "घुसपैठ करें। आगे बढ़ें। सच्चाई का पता लगाएं।"
        ]
    },
    {
        "code": "id",
        "lang": "Indonesian",
        "short": "Rencanakan, menyusup, dan lakukan parkour dalam pencurian",
        "intro": "Infiltrate: Shadow Heist adalah game aksi-siluet 2D tentang presisi, pergerakan lincah, dan menyusup tanpa terlihat.",
        "story": "Di bawah lindungan malam, Anda menyusup ke galangan kapal industri yang dijaga ketat untuk misi pencurian berisiko tinggi. Tujuannya: temukan Kontainer 17 dan ambil isinya. Namun setelah menembus perimeter, misi berubah—kontainer telah dipindahkan, keamanan siaga penuh, dan jejak misterius membawa Anda jauh ke terowongan bawah tanah.\n\nDi sana, di dalam sel tahanan, Anda menemukan seorang tahanan misterius yang terhubung dengan masa lalu Anda.\n\nBebaskan dia, lewati sistem keamanan mematikan, dan lakukan pelarian mustahil.",
        "headers": [
            "Kuasai seni penyusupan",
            "Bergeraklah hati-hati, atur waktu tindakan Anda, sembunyi dari penjaga, dan manfaatkan lingkungan untuk membuka jalur.",
            "Parkour melalui lokasi berbahaya",
            "Lompat, jongkok, memanjat, berayun, dan lewati lingkungan menantang yang penuh rintangan nyata.",
            "Pecahkan teka-teki lingkungan",
            "Gunakan peralatan kargo, kontainer, lift, dan penghalang untuk membuka jalan.",
            "Tetap tak terlihat",
            "Penjaga selalu mengawasi. Satu kesalahan akan membongkar posisi Anda dan memaksa mengulang kembali.",
            "Ikuti misterinya",
            "Setiap misi mengungkap bagian cerita baru saat Anda melacak Kontainer 17 dan berjuang kabur bersama.",
            "Menyusup. Bergerak. Temukan kebenarannya."
        ]
    },
    {
        "code": "ja-JP",
        "lang": "Japanese",
        "short": "計画し、忍び込み、パルクールで危険な強奪作戦を遂行せよ",
        "intro": "『Infiltrate: Shadow Heist』は、正確な操作、緻密な動き、そして誰にも見つからずに潜入することを追求した2Dステルスアクションゲームです。",
        "story": "闇夜に紛れ、厳重に警備された工業造船所に潜入せよ。目的は第17コンテナの特定とその中身の奪取。しかし敷地内へ侵入した直後、事態は急変する。コンテナはすでに移送され、警戒レベルは最大となり、不気味な痕跡があなたを地下の制限区域へと引きずり込んでいく。\n\n地下収容所の鉄格子の中、あなたと同じ過去を背負った謎の囚人を発見する。\n\n彼を救出し、張り巡らされた防衛網を突破し、前代未聞の脱出劇を完遂せよ。",
        "headers": [
            "潜入術を極めよ",
            "慎重に身を潜め、タイミングを計り、警備兵の目を盗みながら環境を利用して道を切り開け。",
            "危険地帯を駆け抜けるパルクール",
            "跳躍、しゃがみ、よじ登り、ロープアクションで、リアルな障害物が阻む難所を踏破せよ。",
            "環境ギミックパズルを解け",
            "貨物機器、コンテナ、昇降リフト、防護壁を駆使して進路を切り開け。",
            "絶対に見つかるな",
            "警備兵の索敵は常に稼働している。一瞬の油断が命取りとなる。",
            "真相を追え",
            "第17コンテナを追跡し、地下を探索し、共に脱出を目指す中で、物語のピースが明らかになっていく。",
            "潜入せよ。駆け抜けろ。真実を暴け。"
        ]
    },
    {
        "code": "ko-KR",
        "lang": "Korean",
        "short": "계획하고, 잠입하고, 파쿠르로 위험한 하이스트를 완수하세요",
        "intro": "Infiltrate: Shadow Heist는 정밀함, 날렵한 움직임, 들키지 않고 잠입하는 것을 핵심으로 하는 2D 잠입 액션 게임입니다.",
        "story": "어둠을 틈타 삼엄한 경비가 펼쳐진 산업 조선소로 위험천만한 잠입을 시도합니다. 목표는 17번 컨테이너의 위치를 확인하고 내부의 물건을 탈취하는 것. 하지만 침투 직후 상황은 급변합니다. 컨테이너는 이미 이동되었고, 경계 태세는 최고조에 달했으며, 수상한 흔적은 당신을 지하 터널 깊은 곳으로 이끕니다.\n\n그곳 수용 구역 갇혀 있는 이는 당신과 과거를 공유하는 의문의 수감자였습니다.\n\n그를 구출하고, 치명적인 보안망을 피해 불가능에 가까운 탈출을 감행하십시오.",
        "headers": [
            "잠입의 기술을 마스터하십시오",
            "조심스럽게 움직이고, 타이밍을 계산하며, 경비병을 따돌리고 주변 지형지물을 활용해 길을 개척하십시오.",
            "위험한 지역을 가로지르는 파쿠르",
            "점프, 웅크리기, 등반, 스윙 액션을 구사하며 현실적인 장애물들로 가득한 난관을 돌파하십시오.",
            "환경 퍼즐 해결",
            "화물 장비, 컨테이너, 리프트 및 차단벽을 조작하여 탈출 경로를 확보하십시오.",
            "들키지 마십시오",
            "경비병의 시선은 항상 감시 중입니다. 단 한 번의 실수로 발각되면 처음부터 다시 시작해야 합니다.",
            "미스터리를 추적하십시오",
            "17번 컨테이너의 뒤를 쫓고, 지하 구역을 헤쳐 나가며 함께 탈출하는 과정에서 숨겨진 진실이 드러납니다.",
            "침투하라. 움직여라. 진실을 밝혀내라."
        ]
    },
    {
        "code": "pt-BR",
        "lang": "Portuguese (Brazil)",
        "short": "Planeje, infiltre-se e faça parkour em assaltos perigosos",
        "intro": "Infiltrate: Shadow Heist é um jogo de ação furtiva em 2D focado em precisão, movimentação e infiltração sem ser detectado.",
        "story": "Sob o manto da escuridão, você se infiltra em um estaleiro industrial fortemente vigiado para um assalto arriscado. O objetivo: localizar o Contêiner 17 e extrair o que está dentro. Mas ao ultrapassar o perímetro, a missão muda: o contêiner foi movido, a segurança está em alerta máximo e uma trilha sombria leva você para túneis subterrâneos restritos.\n\nLá, trancado nas celas, você descobre um prisioneiro misterioso que compartilha o seu passado.\n\nLiberte-o, atravesse barreiras letais e execute uma fuga impossível.",
        "headers": [
            "Domine a arte da infiltração",
            "Mova-se com cuidado, cronometre suas ações, esconda-se dos guardas e use o cenário para criar seu próprio caminho.",
            "Parkour por locais perigosos",
            "Pule, abaixe-se, escale, balance e avance por ambientes desafiadores construídos com obstáculos realistas.",
            "Resolva enigmas ambientais",
            "Use equipamentos de carga, contêineres, elevadores e barreiras para abrir rotas e superar obstáculos.",
            "Permaneça invisível",
            "Os guardas estão sempre vigilantes. Um erro pode expor você e forçar a tentar novamente.",
            "Siga o mistério",
            "Cada missão revela outra parte da história enquanto você rastreia o Contêiner 17 e luta para escapar juntos.",
            "Infiltre-se. Mova-se. Descubra a verdade."
        ]
    },
    {
        "code": "pt-PT",
        "lang": "Portuguese (Portugal)",
        "short": "Planeie, infiltre-se e faça parkour em assaltos perigosos",
        "intro": "Infiltrate: Shadow Heist é um jogo de ação furtiva em 2D focado na precisão, movimentação e infiltração sem ser visto.",
        "story": "Sob a cobertura da noite, infiltra-se num estaleiro industrial fortemente vigiado para um assalto de alto risco. O objetivo: localizar o Contentor 17 e extrair o seu conteúdo. Mas após ultrapassar o perímetro, a missão muda — o contentor foi movido, a segurança está em alerta máximo e um rastro misterioso conduz a túneis subterrâneos restritos.\n\nLá, fechado numa cela, descobre um prisioneiro misterioso que partilha o seu passado.\n\nLiberte-o, contorne barreiras de segurança mortíferas e execute uma fuga impossível.",
        "headers": [
            "Domine a arte da infiltração",
            "Mova-se com cuidado, cronometre as suas ações, oculte-se dos guardas e aproveite o cenário para criar o seu próprio caminho.",
            "Parkour por locais perigosos",
            "Salte, agache-se, trepe, balance e avance por ambientes desafiantes repletos de obstáculos realistas.",
            "Resolva quebra-cabeças ambientais",
            "Utilize equipamentos de carga, contentores, elevadores e barreiras para abrir rotas.",
            "Permaneça invisível",
            "Os guardas estão sempre atentos. Um único erro pode denunciá-lo e obrigar a recomeçar.",
            "Siga o mistério",
            "Cada missão revela um novo elemento da história enquanto rastreia o Contentor 17 e lutam para escapar juntos.",
            "Infiltre-se. Mova-se. Descubra a verdade."
        ]
    },
    {
        "code": "ru-RU",
        "lang": "Russian",
        "short": "Планируй, крадись и совершай паркур в опасных ограблениях",
        "intro": "Infiltrate: Shadow Heist — это 2D стелс-экшен о точности, расчетливых движениях и скрытном проникновении.",
        "story": "Под покровом ночи вы проникаете на охраняемую верфь ради рискованного ограбления. Цель: найти Контейнер 17 и извлечь его содержимое. Но как только периметр пройден, операция срывается — контейнер перемещен, охрана поднята по тревоге, а таинственный след уводит в глубь подземных туннелей.\n\nТам, в запертой камере, вы обнаруживаете загадочного пленника, связанного с вашим прошлым.\n\nОсвободите его, обойдите смертоносные охранные рубежи и совершите дерзкий побег.",
        "headers": [
            "Освойте искусство скрытности",
            "Двигайтесь бесшумно, выверяйте тайминги, прячьтесь от охраны и используйте окружение для создания пути.",
            "Паркур в опасных зонах",
            "Прыгайте, приседайте, взбирайтесь на уступы, раскачивайтесь на тросах среди реалистичных препятствий.",
            "Решайте пространственные головоломки",
            "Используйте грузовые подъемники, контейнеры, лифты и рубильники для открытия маршрутов.",
            "Оставайтесь незамеченным",
            "Патрули не дремлют. Малейшая ошибка поднимет тревогу и заставит начать заново.",
            "Раскройте тайну",
            "Каждая миссия открывает новую часть истории по мере выслеживания Контейнера 17 и совместного побега.",
            "Проникни. Двигайся. Узнай правду."
        ]
    },
    {
        "code": "es-419",
        "lang": "Spanish (Latin America)",
        "short": "Planea, infíltrate y haz parkour en atracos peligrosos",
        "intro": "Infiltrate: Shadow Heist es un juego de acción y sigilo en 2D sobre precisión, movimiento e infiltración sin ser detectado.",
        "story": "Bajo el manto de la noche, te infiltras en un astillero industrial fuertemente custodiado en un atraco de alto riesgo. El objetivo: localizar el Contenedor 17 y extraer lo que hay dentro. Pero al cruzar el perímetro, la misión cambia: el contenedor ha sido trasladado, la seguridad está en máxima alerta y un rastro sombrío te arrastra hacia túneles subterráneos restringidos.\n\nAllí, encerrado en las celdas, descubres a un prisionero misterioso que comparte tu pasado.\n\nLibéralo, esquiva redes de seguridad letales y ejecuta una huida imposible.",
        "headers": [
            "Domina el arte de la infiltración",
            "Muévete con cuidado, calcula tus tiempos, elude a los guardias y aprovecha el entorno para abrirte paso.",
            "Haz parkour en zonas peligrosas",
            "Salta, agáchate, trepa, balancéate y avanza por escenarios desafiantes llenos de obstáculos realistas.",
            "Resuelve acertijos ambientales",
            "Utiliza equipo de carga, contenedores, elevadores y barreras para desbloquear rutas.",
            "Mantente invisible",
            "Los guardias vigilan sin descanso. Un solo descuido te expondrá y te obligará a reiniciar.",
            "Sigue el misterio",
            "Cada misión revela otra pieza de la historia mientras rastreas el Contenedor 17 y luchas por escapar juntos.",
            "Infíltrate. Muévete. Descubre la verdad."
        ]
    },
    {
        "code": "es-ES",
        "lang": "Spanish (Spain)",
        "short": "Planea, infíltrate y haz parkour en atracos peligrosos",
        "intro": "Infiltrate: Shadow Heist es un juego de acción y sigilo en 2D sobre precisión, movimiento e infiltración sin ser detectado.",
        "story": "Bajo el manto de la noche, te infiltras en un astillero industrial fuertemente custodiado en un atraco de alto riesgo. El objetivo: localizar el Contenedor 17 y extraer lo que hay dentro. Pero al cruzar el perímetro, la misión cambia: el contenedor ha sido trasladado, la seguridad está en máxima alerta y un rastro sombrío te arrastra hacia túneles subterráneos restringidos.\n\nAllí, encerrado en las celdas, descubres a un prisionero misterioso que comparte tu pasado.\n\nLibéralo, esquiva redes de seguridad letales y ejecuta una huida imposible.",
        "headers": [
            "Domina el arte de la infiltración",
            "Muévete con cuidado, calcula tus tiempos, elude a los guardias y aprovecha el entorno para abrirte paso.",
            "Haz parkour en zonas peligrosas",
            "Salta, agáchate, trepa, balancéate y avanza por escenarios desafiantes llenos de obstáculos realistas.",
            "Resuelve acertijos ambientales",
            "Utiliza equipo de carga, contenedores, elevadores y barreras para desbloquear rutas.",
            "Mantente invisible",
            "Los guardias vigilan sin descanso. Un solo descuido te expondrá y te obligará a reiniciar.",
            "Sigue el misterio",
            "Cada misión revela otra pieza de la historia mientras rastreas el Contenedor 17 y luchas por escapar juntos.",
            "Infíltrate. Muévete. Descubre la verdad."
        ]
    },
    {
        "code": "es-US",
        "lang": "Spanish (United States)",
        "short": "Planea, infíltrate y haz parkour en atracos peligrosos",
        "intro": "Infiltrate: Shadow Heist es un juego de acción y sigilo en 2D sobre precisión, movimiento e infiltración sin ser detectado.",
        "story": "Bajo el manto de la noche, te infiltras en un astillero industrial fuertemente custodiado en un atraco de alto riesgo. El objetivo: localizar el Contenedor 17 y extraer lo que hay dentro. Pero al cruzar el perímetro, la misión cambia: el contenedor ha sido trasladado, la seguridad está en máxima alerta y un rastro sombrío te arrastra hacia túneles subterráneos restringidos.\n\nAllí, encerrado en las celdas, descubres a un prisionero misterioso que comparte tu pasado.\n\nLibéralo, esquiva redes de seguridad letales y ejecuta una huida imposible.",
        "headers": [
            "Domina el arte de la infiltración",
            "Muévete con cuidado, calcula tus tiempos, elude a los guardias y aprovecha el entorno para abrirte paso.",
            "Haz parkour en zonas peligrosas",
            "Salta, agáchate, trepa, balancéate y avanza por escenarios desafiantes llenos de obstáculos realistas.",
            "Resuelve acertijos ambientales",
            "Utiliza equipo de carga, contenedores, elevadores y barreras para desbloquear rutas.",
            "Mantente invisible",
            "Los guardias vigilan sin descanso. Un solo descuido te expondrá y te obligará a reiniciar.",
            "Sigue el misterio",
            "Cada misión revela otra pieza de la historia mientras rastreas el Contenedor 17 y luchas por escapar juntos.",
            "Infíltrate. Muévete. Descubre la verdad."
        ]
    },
    {
        "code": "th",
        "lang": "Thai",
        "short": "วางแผน ลอบเร้น และพาร์กัวร์ผ่านการโจรกรรมสุดอันตราย",
        "intro": "Infiltrate: Shadow Heist คือเกมแอ็กชันลอบเร้น 2D ที่เน้นความแม่นยำ การเคลื่อนไหว และการแทรกซึมเข้าไปโดยไม่ให้ใครเห็น",
        "story": "ภายใต้ความมืดมิด คุณได้แทรกซึมเข้าไปในอู่ต่อเรือที่มีการคุ้มกันอย่างแน่นหนาในภารกิจโจรกรรมสุดเสี่ยง เป้าหมายคือ: ค้นหาตู้คอนเทนเนอร์ 17 และชิงสิ่งที่อยู่ข้างใน แต่เมื่อผ่านแนวรั้วเข้าไป ภารกิจกลับพลิกผัน—ตู้คอนเทนเนอร์ถูกย้ายไปแล้ว ระบบรักษาความปลอดภัยตื่นตัวสูงสุด และร่องรอยลึกลับก็นำคุณดำดิ่งลงสู่อุโมงค์ใต้ดินเขตหวงห้าม\n\nที่นั่น ภายในห้องขัง คุณได้พบกับนักโทษปริศนาผู้มีอดีตร่วมกับคุณ\n\nพาเขาแหกคุกออกมา ฝ่าระบบเลเซอร์มรณะ และเปิดฉากการหลบหนีที่เป็นไปไม่ได้",
        "headers": [
            "ฝึกฝนศิลปะแห่งการลอบเร้น",
            "เคลื่อนที่อย่างระมัดระวัง กะจังหวะเวลา หลบซ่อนจากยาม และใช้สภาพแวดล้อมเพื่อสร้างเส้นทางของคุณเอง",
            "พาร์กัวร์ผ่านจุดอันตราย",
            "กระโดด หมอบ ปีนป่าย โหนตัว และฝ่าฟันอุปสรรคที่สมจริงในสภาพแวดล้อมสุดท้าทาย",
            "ไขปริศนากลไกในฉาก",
            "ใช้เครื่องมือขนส่ง ตู้สินค้า ลิฟต์ และแผงกั้นเพื่อเปิดเส้นทาง",
            "อย่าให้ใครเห็น",
            "ยามเฝ้ามองอยู่เสมอ ความผิดพลาดเพียงครั้งเดียวอาจเผยตำแหน่งของคุณและทำให้ต้องเริ่มใหม่",
            "ติดตามความลึกลับ",
            "ทุกภารกิจจะเปิดเผยเรื่องราวอีกชิ้นหนึ่ง ขณะที่คุณแกะรอยตู้คอนเทนเนอร์ 17 และต่อสู้เพื่อหนีรอดไปด้วยกัน",
            "แทรกซึม เคลื่อนไหว ค้นพบความจริง"
        ]
    },
    {
        "code": "tr-TR",
        "lang": "Turkish",
        "short": "Tehlikeli soygunlarda plan yap, sız ve parkur yeteneklerini göster",
        "intro": "Infiltrate: Shadow Heist, hassasiyet, hareket kabiliyeti ve fark edilmeden içeri sızma üzerine kurulu bir 2D gizlilik-aksiyon oyunudur.",
        "story": "Karanlığın örtüsü altında, yüksek riskli bir soygun için sıkı korunan bir sanayi tersanesine sızıyorsunuz. Hedef: 17 Numaralı Konteyneri bulmak ve içindekini ele geçirmek. Ancak çevre hattını aştığınız anda görev değişir; konteyner taşınmış, güvenlik en üst seviyeye geçmiş ve gizemli bir iz sizi yeraltı servis tünellerine çekmiştir.\n\nOrada, hücrelerin içinde, geçmişinizi paylaşan gizemli bir mahkûm keşfedersiniz.\n\nOnu kurtarın, ölümcül güvenlik ağlarını aşın ve imkânsız bir kaçışı gerçekleştirin.",
        "headers": [
            "Sızma sanatında ustalaşın",
            "Dikkatle ilerleyin, adımlarınızı zamanlayın, nöbetçilerden saklanın ve çevreyi kullanarak kendi yolunuzu çizin.",
            "Tehlikeli bölgelerde parkur yapın",
            "Gerçekçi nesneler ve engellerle kurulu zorlu ortamlarda zıplayın, eğilin, tırmanın ve sallanın.",
            "Çevresel bulmacaları çözün",
            "Rotaları açmak için yük ekipmanlarını, konteynerleri, asansörleri ve bariyerleri kullanın.",
            "Görünmez kalın",
            "Muhafızlar her an tetikte. Tek bir hata sizi ele verebilir ve baştan başlamak zorunda bırakabilir.",
            "Gizemi takip edin",
            "17 Numaralı Konteynerin izini sürerken ve birlikte kaçış mücadelesi verirken her görev hikâyenin yeni bir parçasını açığa çıkarır.",
            "Sız. İlerle. Gerçeği keşfet."
        ]
    },
    {
        "code": "vi",
        "lang": "Vietnamese",
        "short": "Lên kế hoạch, lén lút và parkour qua các phi vụ nguy hiểm",
        "intro": "Infiltrate: Shadow Heist là tựa game hành động lén lút 2D đòi hỏi sự chính xác, khả năng di chuyển và đột nhập mà không bị phát hiện.",
        "story": "Dưới bóng đêm bao phủ, bạn đột nhập vào một xưởng đóng tàu công nghiệp được bảo vệ nghiêm ngặt để thực hiện một vụ trộm rủi ro cao. Mục tiêu: xác định vị trí Container 17 và lấy đi thứ bên trong. Nhưng khi vượt qua vành đai, nhiệm vụ đổi hướng—container đã bị chuyển đi, an ninh báo động tối đa và một dấu vết mờ ám kéo bạn xuống sâu dưới đường hầm bí mật.\n\nTại đó, bị nhốt trong phòng giam, bạn phát hiện một tù nhân bí ẩn có liên hệ với quá khứ của bạn.\n\nGiải cứu anh ta, vượt qua lưới bảo vệ chết người và thực hiện một cuộc đào thoát không tưởng.",
        "headers": [
            "Làm chủ nghệ thuật lén lút",
            "Di chuyển cẩn thận, căn chuẩn thời cơ, ẩn nấp khỏi lính gác và tận dụng địa hình để tạo lối đi.",
            "Parkour qua các địa điểm nguy hiểm",
            "Nhảy, cúi người, leo trèo, đu dây và vượt qua các môi trường đầy thử thách với chướng ngại vật thực tế.",
            "Giải các câu đố cơ quan địa hình",
            "Sử dụng thiết bị bốc dỡ, thùng hàng, thang máy và cổng chắn để mở đường.",
            "Tuyệt đối không bị phát hiện",
            "Lính gác luôn cảnh giác cao độ. Chỉ một sai lầm nhỏ cũng khiến bạn bại lộ và phải làm lại từ đầu.",
            "Lần theo dấu vết bí ẩn",
            "Mỗi nhiệm vụ mở ra một phần của câu chuyện khi bạn truy vết Container 17 và cùng nhau vượt ngục.",
            "Đột nhập. Hành động. Khám phá sự thật."
        ]
    }
]

def build_full_desc(item):
    intro = item["intro"]
    story = item["story"]
    h = item["headers"]
    return f"{intro}\n\n{story}\n\n{h[0]}\n{h[1]}\n\n{h[2]}\n{h[3]}\n\n{h[4]}\n{h[5]}\n\n{h[6]}\n{h[7]}\n\n{h[8]}\n{h[9]}\n\n{h[10]}"

with open("marketing/store-translations.csv", "w", encoding="utf-8", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["Language Code", "Language", "App name", "Short description", "Full description"])
    for item in DATA:
        full = build_full_desc(item)
        writer.writerow([item["code"], item["lang"], "Infiltrate: Shadow Heist", item["short"], full])

print(f"Generated {len(DATA)} entries in marketing/store-translations.csv successfully!")
