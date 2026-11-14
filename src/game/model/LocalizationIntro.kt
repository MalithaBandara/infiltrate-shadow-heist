package game.model

/** One subtitle of the intro video, on screen from [start] to [end] seconds of `intro.mp4`. */
data class IntroSubtitleCue(val start: Double, val end: Double)

/**
 * Subtitles for the cold-launch intro (`intro.mp4`, 59.5s of English narration).
 *
 * The timings came from the narration itself (2026-11-14): a local Whisper transcription with
 * word timestamps, each cue shown 0.15s before its words and held 0.5s after, never into the next
 * cue. A sentence longer than ~42 characters that holds two sentences is split at its full stop.
 * Re-time if the video is re-cut. The English is the narration word for word ("the Copier"
 * capitalised as the machine's name); the other 16 languages are machine-drafted translations,
 * not reviewed by native speakers.
 *
 * Off by default for English and on for every other language - see [defaultOn] and
 * `GameProfile.subtitlesOn`. Shown by `ui/IntroVideo.kt`'s `IntroSequence`.
 */
object IntroSubtitles {
    val CUES = listOf(
        IntroSubtitleCue(2.14, 8.25),
        IntroSubtitleCue(12.42, 16.37),
        IntroSubtitleCue(17.26, 19.59),
        IntroSubtitleCue(19.64, 22.41),
        IntroSubtitleCue(22.46, 26.03),
        IntroSubtitleCue(26.80, 30.91),
        IntroSubtitleCue(32.04, 34.33),
        IntroSubtitleCue(34.60, 37.03),
        IntroSubtitleCue(37.80, 41.07),
        IntroSubtitleCue(43.05, 45.04),
        IntroSubtitleCue(45.69, 47.92),
        IntroSubtitleCue(48.41, 52.00),
        IntroSubtitleCue(54.05, 56.70),
        IntroSubtitleCue(56.75, 59.22)
    )

    /** Subtitles start on for every language but English, where the narration needs none. */
    fun defaultOn(lang: String): Boolean = Localization.langCode(lang) != Localization.EN

    /** Index into [CUES] of the cue on screen at [seconds], or null between cues. */
    fun cueAt(seconds: Double): Int? {
        for (i in CUES.indices) {
            if (seconds >= CUES[i].start && seconds < CUES[i].end) return i
        }
        return null
    }

    /** The text of cue [index] in [lang] (English for an unknown language). */
    fun text(index: Int, lang: String): String =
        (LINES[Localization.langCode(lang)] ?: LINES.getValue(Localization.EN))[index]

    /** Every language's lines, in [CUES] order. A new language needs an entry here too. */
    val LINES: Map<String, List<String>> = mapOf(
        Localization.EN to listOf(
            "Five years ago, two thieves were hired for one impossible job.",
            "Their target was a machine no one was supposed to know existed.",
            "They called it the Copier.",
            "No one knew what it was truly capable of.",
            "Before they could take it, the machine came alive.",
            "When the light faded, one of them was gone.",
            "The machine fell silent.",
            "His partner was gone.",
            "And for five years, he tried to forget.",
            "Five years had passed.",
            "He had left the past behind.",
            "Until something from that night found him again.",
            "The past had one final secret.",
            "The shipyard held the next clue."
        ),
        Localization.FR to listOf(
            "Il y a cinq ans, deux voleurs ont été engagés pour un travail impossible.",
            "Leur cible : une machine dont personne n'était censé connaître l'existence.",
            "On l'appelait le Copieur.",
            "Personne ne savait de quoi elle était vraiment capable.",
            "Avant qu'ils puissent s'en emparer, la machine s'est réveillée.",
            "Quand la lumière s'est dissipée, l'un d'eux avait disparu.",
            "La machine s'est tue.",
            "Son partenaire avait disparu.",
            "Et pendant cinq ans, il a tenté d'oublier.",
            "Cinq ans ont passé.",
            "Il avait laissé le passé derrière lui.",
            "Jusqu'à ce que quelque chose de cette nuit-là le retrouve.",
            "Le passé cachait un dernier secret.",
            "Le chantier naval détenait le prochain indice."
        ),
        Localization.IT to listOf(
            "Cinque anni fa, due ladri furono ingaggiati per un colpo impossibile.",
            "Il loro obiettivo era una macchina di cui nessuno doveva conoscere l'esistenza.",
            "La chiamavano la Copiatrice.",
            "Nessuno sapeva di cosa fosse davvero capace.",
            "Prima che potessero prenderla, la macchina si risvegliò.",
            "Quando la luce svanì, uno di loro era scomparso.",
            "La macchina tacque.",
            "Il suo compagno era scomparso.",
            "E per cinque anni cercò di dimenticare.",
            "Erano passati cinque anni.",
            "Si era lasciato il passato alle spalle.",
            "Finché qualcosa di quella notte non lo ritrovò.",
            "Il passato custodiva un ultimo segreto.",
            "Il cantiere navale nascondeva il prossimo indizio."
        ),
        Localization.ES to listOf(
            "Hace cinco años, dos ladrones fueron contratados para un trabajo imposible.",
            "Su objetivo era una máquina cuya existencia nadie debía conocer.",
            "La llamaban la Copiadora.",
            "Nadie sabía de qué era realmente capaz.",
            "Antes de que pudieran llevársela, la máquina cobró vida.",
            "Cuando la luz se apagó, uno de ellos había desaparecido.",
            "La máquina quedó en silencio.",
            "Su compañero había desaparecido.",
            "Y durante cinco años, intentó olvidar.",
            "Habían pasado cinco años.",
            "Había dejado atrás el pasado.",
            "Hasta que algo de aquella noche volvió a encontrarlo.",
            "El pasado guardaba un último secreto.",
            "El astillero guardaba la siguiente pista."
        ),
        Localization.PT_BR to listOf(
            "Cinco anos atrás, dois ladrões foram contratados para um trabalho impossível.",
            "O alvo era uma máquina cuja existência ninguém deveria conhecer.",
            "Eles a chamavam de Copiadora.",
            "Ninguém sabia do que ela era realmente capaz.",
            "Antes que pudessem levá-la, a máquina ganhou vida.",
            "Quando a luz se apagou, um deles tinha sumido.",
            "A máquina ficou em silêncio.",
            "O parceiro dele tinha sumido.",
            "E por cinco anos, ele tentou esquecer.",
            "Cinco anos se passaram.",
            "Ele tinha deixado o passado para trás.",
            "Até que algo daquela noite o encontrou de novo.",
            "O passado guardava um último segredo.",
            "O estaleiro guardava a próxima pista."
        ),
        Localization.DE to listOf(
            "Vor fünf Jahren wurden zwei Diebe für einen unmöglichen Auftrag angeheuert.",
            "Ihr Ziel war eine Maschine, von deren Existenz niemand wissen sollte.",
            "Man nannte sie den Kopierer.",
            "Niemand wusste, wozu sie wirklich fähig war.",
            "Bevor sie sie mitnehmen konnten, erwachte die Maschine zum Leben.",
            "Als das Licht erlosch, war einer von ihnen verschwunden.",
            "Die Maschine verstummte.",
            "Sein Partner war verschwunden.",
            "Und fünf Jahre lang versuchte er zu vergessen.",
            "Fünf Jahre waren vergangen.",
            "Er hatte die Vergangenheit hinter sich gelassen.",
            "Bis ihn etwas aus jener Nacht wieder einholte.",
            "Die Vergangenheit hütete ein letztes Geheimnis.",
            "Die Werft barg den nächsten Hinweis."
        ),
        Localization.ID to listOf(
            "Lima tahun lalu, dua pencuri disewa untuk satu pekerjaan mustahil.",
            "Sasaran mereka adalah mesin yang keberadaannya tak boleh diketahui siapa pun.",
            "Mereka menyebutnya Si Penyalin.",
            "Tak ada yang tahu apa yang sebenarnya mampu dilakukannya.",
            "Sebelum mereka sempat mengambilnya, mesin itu hidup.",
            "Saat cahaya meredup, salah satu dari mereka telah lenyap.",
            "Mesin itu pun terdiam.",
            "Rekannya telah lenyap.",
            "Dan selama lima tahun, ia berusaha melupakannya.",
            "Lima tahun telah berlalu.",
            "Ia telah meninggalkan masa lalunya.",
            "Hingga sesuatu dari malam itu kembali menemukannya.",
            "Masa lalu menyimpan satu rahasia terakhir.",
            "Galangan kapal menyimpan petunjuk berikutnya."
        ),
        Localization.TR to listOf(
            "Beş yıl önce, iki hırsız imkânsız bir iş için tutuldu.",
            "Hedefleri, varlığından kimsenin haberdar olmaması gereken bir makineydi.",
            "Ona Kopyalayıcı diyorlardı.",
            "Gerçekte neler yapabildiğini kimse bilmiyordu.",
            "Onu alamadan makine canlandı.",
            "Işık söndüğünde, içlerinden biri yok olmuştu.",
            "Makine sustu.",
            "Ortağı yok olmuştu.",
            "Ve beş yıl boyunca unutmaya çalıştı.",
            "Beş yıl geçmişti.",
            "Geçmişi geride bırakmıştı.",
            "Ta ki o geceden bir şey onu yeniden bulana dek.",
            "Geçmişin son bir sırrı vardı.",
            "Bir sonraki ipucu tersanedeydi."
        ),
        Localization.VI to listOf(
            "Năm năm trước, hai tên trộm được thuê cho một phi vụ bất khả thi.",
            "Mục tiêu của họ là một cỗ máy mà không ai được phép biết đến.",
            "Người ta gọi nó là Máy Sao Chép.",
            "Không ai biết nó thực sự có thể làm được gì.",
            "Trước khi họ kịp lấy nó, cỗ máy bỗng sống dậy.",
            "Khi ánh sáng tắt dần, một người trong số họ đã biến mất.",
            "Cỗ máy im bặt.",
            "Người cộng sự của anh đã biến mất.",
            "Và suốt năm năm, anh cố gắng quên đi.",
            "Năm năm đã trôi qua.",
            "Anh đã bỏ lại quá khứ phía sau.",
            "Cho đến khi một điều gì đó từ đêm ấy lại tìm đến anh.",
            "Quá khứ còn một bí mật cuối cùng.",
            "Xưởng đóng tàu nắm giữ manh mối tiếp theo."
        ),
        Localization.RU to listOf(
            "Пять лет назад двух воров наняли для одного невозможного дела.",
            "Их целью была машина, о существовании которой никто не должен был знать.",
            "Её называли Копировщиком.",
            "Никто не знал, на что она на самом деле способна.",
            "Прежде чем они успели её забрать, машина ожила.",
            "Когда свет погас, один из них исчез.",
            "Машина затихла.",
            "Его напарник пропал.",
            "И пять лет он пытался забыть.",
            "Прошло пять лет.",
            "Он оставил прошлое позади.",
            "Пока что-то из той ночи не нашло его снова.",
            "У прошлого оставалась последняя тайна.",
            "Следующая зацепка ждала на верфи."
        ),
        Localization.JA to listOf(
            "5年前、2人の泥棒がある不可能な仕事のために雇われた。",
            "標的は、誰も存在を知るはずのない機械だった。",
            "それは「コピアー」と呼ばれていた。",
            "その本当の力を知る者はいなかった。",
            "奪い去る前に、機械が目を覚ました。",
            "光が消えたとき、1人の姿がなかった。",
            "機械は沈黙した。",
            "相棒は消えていた。",
            "そして5年間、彼は忘れようとした。",
            "5年が過ぎた。",
            "彼は過去を捨てていた。",
            "あの夜の何かが、再び彼を見つけるまでは。",
            "過去には最後の秘密が残されていた。",
            "次の手がかりは造船所にあった。"
        ),
        Localization.KO to listOf(
            "5년 전, 두 도둑이 불가능한 임무를 위해 고용되었다.",
            "그들의 목표는 아무도 존재를 알아서는 안 되는 기계였다.",
            "사람들은 그것을 '복제기'라고 불렀다.",
            "그것이 진짜로 무엇을 할 수 있는지 아는 사람은 없었다.",
            "그들이 손에 넣기도 전에, 기계가 깨어났다.",
            "빛이 사라졌을 때, 그중 한 명이 사라져 있었다.",
            "기계는 침묵했다.",
            "그의 동료는 사라졌다.",
            "그리고 5년 동안, 그는 잊으려 애썼다.",
            "5년이 흘렀다.",
            "그는 과거를 뒤로했다.",
            "그날 밤의 무언가가 다시 그를 찾아오기 전까지는.",
            "과거에는 마지막 비밀이 하나 남아 있었다.",
            "다음 단서는 조선소에 있었다."
        ),
        Localization.ZH_TW to listOf(
            "五年前，兩名竊賊受雇執行一項不可能的任務。",
            "他們的目標，是一台沒人該知道其存在的機器。",
            "他們稱它為「複製機」。",
            "沒有人知道它真正的能耐。",
            "他們還來不及帶走它，機器便甦醒了。",
            "當光芒消退，其中一人已不見蹤影。",
            "機器歸於沉寂。",
            "他的搭檔消失了。",
            "五年來，他一直試著遺忘。",
            "五年過去了。",
            "他已將過去拋在身後。",
            "直到那一夜的某樣東西再次找上他。",
            "過去還藏著最後一個秘密。",
            "下一條線索就在造船廠。"
        ),
        Localization.HI to listOf(
            "पाँच साल पहले, दो चोरों को एक नामुमकिन काम के लिए रखा गया था।",
            "उनका निशाना एक ऐसी मशीन थी जिसके होने के बारे में किसी को पता नहीं होना चाहिए था।",
            "वे उसे कॉपियर कहते थे।",
            "कोई नहीं जानता था कि वह असल में क्या कर सकती है।",
            "इससे पहले कि वे उसे ले जा पाते, मशीन जाग उठी।",
            "जब रोशनी बुझी, तो उनमें से एक गायब था।",
            "मशीन खामोश हो गई।",
            "उसका साथी गायब हो चुका था।",
            "और पाँच साल तक, उसने भूलने की कोशिश की।",
            "पाँच साल बीत गए।",
            "उसने अतीत को पीछे छोड़ दिया था।",
            "जब तक कि उस रात की किसी चीज़ ने उसे फिर से ढूँढ़ नहीं लिया।",
            "अतीत में एक आख़िरी राज़ छिपा था।",
            "अगला सुराग शिपयार्ड में था।"
        ),
        Localization.AR to listOf(
            "قبل خمس سنوات، استُؤجر لصّان لمهمة واحدة مستحيلة.",
            "كان هدفهما آلةً لم يكن يُفترض بأحد أن يعلم بوجودها.",
            "كانوا يسمّونها «الناسخة».",
            "لم يعرف أحد ما الذي كانت قادرة عليه حقًا.",
            "قبل أن يتمكّنا من أخذها، دبّت الحياة في الآلة.",
            "وحين خبا الضوء، كان أحدهما قد اختفى.",
            "صمتت الآلة.",
            "اختفى شريكه.",
            "وطوال خمس سنوات، حاول أن ينسى.",
            "مرّت خمس سنوات.",
            "كان قد ترك الماضي وراءه.",
            "إلى أن عثر عليه شيءٌ من تلك الليلة من جديد.",
            "كان الماضي يخفي سرًّا أخيرًا.",
            "كان حوض بناء السفن يحمل الدليل التالي."
        ),
        Localization.FA to listOf(
            "پنج سال پیش، دو دزد برای یک کار غیرممکن استخدام شدند.",
            "هدفشان دستگاهی بود که قرار نبود کسی از وجودش باخبر شود.",
            "اسمش را «نسخه‌بردار» گذاشته بودند.",
            "هیچ‌کس نمی‌دانست واقعاً از پس چه کاری برمی‌آید.",
            "پیش از آنکه بتوانند آن را ببرند، دستگاه جان گرفت.",
            "وقتی نور فرو نشست، یکی از آن‌ها ناپدید شده بود.",
            "دستگاه خاموش شد.",
            "شریکش رفته بود.",
            "و پنج سال تمام، تلاش کرد فراموش کند.",
            "پنج سال گذشته بود.",
            "او گذشته را پشت سر گذاشته بود.",
            "تا اینکه چیزی از آن شب دوباره پیدایش کرد.",
            "گذشته یک راز آخر داشت.",
            "سرنخ بعدی در کشتی‌سازی بود."
        ),
        Localization.TH to listOf(
            "ห้าปีก่อน หัวขโมยสองคนถูกจ้างให้ทำงานที่เป็นไปไม่ได้",
            "เป้าหมายของพวกเขาคือเครื่องจักรที่ไม่ควรมีใครรู้ว่ามีอยู่จริง",
            "พวกเขาเรียกมันว่า \"เครื่องคัดลอก\"",
            "ไม่มีใครรู้ว่ามันทำอะไรได้บ้างจริงๆ",
            "ก่อนที่พวกเขาจะเอามันไปได้ เครื่องจักรก็ตื่นขึ้น",
            "เมื่อแสงจางหาย หนึ่งในพวกเขาก็หายไป",
            "เครื่องจักรเงียบลง",
            "คู่หูของเขาหายไปแล้ว",
            "และตลอดห้าปี เขาพยายามลืมมัน",
            "ห้าปีผ่านไป",
            "เขาทิ้งอดีตไว้เบื้องหลังแล้ว",
            "จนกระทั่งบางสิ่งจากคืนนั้นตามหาเขาเจออีกครั้ง",
            "อดีตยังมีความลับสุดท้ายซ่อนอยู่",
            "อู่ต่อเรือมีเบาะแสถัดไป"
        )
    )
}
