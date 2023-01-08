package org.totschnig.playstoreapi

import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.InAppProduct
import com.google.api.services.androidpublisher.model.InAppProductListing
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import java.io.IOException
import java.security.GeneralSecurityException

class Main {
    companion object {
        const val APPLICATION_NAME = "MyExpensesMobi-Application/1.0"

        private const val PACKAGE_NAME = "org.totschnig.myexpenses"

        //@formatter:off
        private val SKU_category = "categorytree" to mapOf(
            "en-US" to ("Category Tree" to "Arbitrarily deep category hierarchy"),
            "bg" to ("Дърво на категориите" to "Произволно дълбока йерархия на категориите"),
            "de-DE" to ("Kategorienbaum" to "Beliebig tiefe Kategorienhierarchie"),
            "es-ES" to ("Árbol de categorías" to "Jerarquía de categorías arbitraria"),
            "fr-FR" to ("Arborescence des catégories" to "Une hiérarchie de catégories arbitrairement profonde"),
            "hr" to ("Stablo kategorija" to "Proizvoljno duboka hijerarhija kategorija"),
            "it-IT" to ("Albero delle categorie" to "Gerarchia di categorie arbitrariamente profonda"),
            "iw-IL" to ("עץ קטגוריות" to "היררכיית קטגוריה עמוקה שרירותית"),
            "kn-IN" to ("ವರ್ಗ ಮರ" to "ನಿರಂಕುಶವಾಗಿ ಆಳವಾದ ವರ್ಗ ಕ್ರಮಾನುಗತ"),
            "pt-PT" to ("Árvore de categorias" to "Hierarquia de categorias arbitrariamente profunda"),
            "te-IN" to ("వర్గం చెట్టు" to "ఏకపక్షంగా లోతైన వర్గం సోపానక్రమం"),
            "tr-TR" to ("Kategori ağacı" to "İstenilen derinlikte kategori hiyerarşisi"),
            "zh-CN" to ("类别树" to "任意深的类别层次结构")
        )

        private val SKU_csv_import = "csvimport" to mapOf(
            "en-US" to ("CSV import" to "Sophisticated, customizable CSV import (spreadsheet applications)"),
            "ar"    to ("إستيراد ملف CSV" to "استيراد CSV متطور وقابل للتخصيص (تطبيقات جداول البيانات)"),
            "bg"    to ("Импорт от CSV" to "Удобен, персонализируем импорт от CSV файл (приложения за електронни таблици)"),
            "ca"    to ("Importació CSV" to " Importació sofisticada i personalitzable CSV (aplicacions de full de càlcul)"),
            "cs-CZ" to ("Import CSV souboru" to "Propracovaný a přizpůsobitelný import CSV (tabulkové aplikace)"),
            "da-DK" to ("CSV import" to "Sofistikeret, brugerdefinerbar CSV import (regnearksprogrammer)"),
            "de-DE" to ("CSV Import" to "Ausgefeilter, anpassbarer CSV-Import (Tabellenkalkulationsanwendungen)"),
            "el-gr" to ("Εισαγωγή CSV" to "Εκλεπτυσμένη, προσαρμόσιμη εισαγωγή CSV (εφαρμογές λογιστικών φύλλων)"),
            "es-ES" to ("Importar desde CSV" to "Una sofisticada y personalizable importación a CSV (para aplicaciones de hojas de cálculo)"),
            "eu-ES" to ("CSV inportazioa" to "CSV inportazio sofistikatu eta pertsonalizagarria (kalkulu orrien aplikazioak)"),
            "fr-FR" to ("Import CSV" to "Import CSV sophistiqué et personnalisable (applications tableur)"),
            "hr"    to ("CSV uvoz" to "Sofisticiran, prilagodljiv CSV uvoz (programi proračunskih tablica)"),
            "hu-HU" to ("CSV-importálás" to "Kifinomult, testreszabható CSV import (táblázatkezelő alkalmazások)"),
            "it-IT" to ("Importa CSV" to "Importazione CSV sofisticata e personalizzabile (applicazioni per fogli di calcolo)"),
            "iw-IL" to ("ייבוא CSV" to "מתוחכם וניתן להתאמה אישית  יבוא CSV   (יישומי גיליון אלקטרוני)"),
            "ja-JP" to ("CSV インポート" to "洗練されたカスタマイズ可能な CSV インポート (スプレッドシート アプリケーション)"),
            "km-KH" to ("ការនាំចូលស៊ីអេសវី" to "ទំនើប ប្ដូរតាមបំណង ការនាំចូល CSV (កម្មវិធីសៀវភៅបញ្ជី)"),
            "kn-IN" to ("ಸಿಎಸ್ವಿ ಆಮದು" to "ಅತ್ಯಾಧುನಿಕ, ಗ್ರಾಹಕೀಯಗೊಳಿಸಬಹುದಾದ CSV ಆಮದು (ಸ್ಪ್ರೆಡ್‌ಶೀಟ್ ಅಪ್ಲಿಕೇಶನ್‌ಗಳು)"),
            "ko-KR" to ("CSV 가져오기" to "정교하고 사용자 정의 가능한 CSV 가져 오기 (스프레드 시트 응용 프로그램)"),
            "ms"    to ("Import CSV" to "Import CSV import (spreadsheet applications) yang canggih dan boleh disuaikan."),
            "pl-PL" to ("Import CSV" to "Wyrafinowany, dostosowywany import CSV (aplikacje do arkuszy kalkulacyjnych)"),
            "pt-PT" to (" Importação CSV" to "Importação de CSV sofisticada e personalizável (aplicativos de planilha)"),
            "ro"    to ("CSV import" to "Import sofisticat și personalizabil CSV (aplicații de foi de calcul)"),
            "ru-RU" to ("Импорт данных в формате CSV" to "Сложный, настраиваемый импорт CSV (приложения для работы с электронными таблицами)"),
            "si-LK" to ("අ.වි.අ. (CSV) ආයාත කරන්න" to "නවීන, අභිරුචිකරණය කළ හැකි CSV ආනයනය (පැතුරුම්පත් යෙදුම්)"),
            "ta-IN" to ("CSV இறக்குமதி" to "அதிநவீன, தனிப்பயனாக்கக்கூடிய CSV இறக்குமதி (விரிதாள் செயலிகள்)"),
            "te-IN" to ("CSV దిగుమతి" to "అధునాతన, అనుకూలీకరించదగిన CSV దిగుమతి (స్ప్రెడ్‌షీట్ అనువర్తనాలు)"),
            "tr-TR" to ("CSV içe aktar" to "Gelişmiş, özelleştirilebilen CSV içe aktarma (tablolama uygulamaları)"),
            "vi"    to ("Nhập CSV" to "Tinh vi, có thể tùy chỉnh  Nhập CSV  (ứng dụng bảng tính)"),
            "zh-CN" to ("导入 CSV" to "可自定义复杂的CSV导入文件（电子表格应用程序）"),
            "zh-TW" to ("以CSV的檔案型式輸入" to "可自定義復雜的CSV導入文件（電子表格應用程序）"),
        )

        private val SKU_csv_distribution = "distribution" to mapOf(
            "en-US" to ("Pie chart" to "Visualize the statistical distribution of transactions per category over different periods (years, months, weeks, days)"),
            "ar" to ("مخطط دائري" to "تصور التوزيع الإحصائي للمعاملات لكل فئة عبر فترات مختلفة (سنوات ، أشهر ، أسابيع ، أيام)"),
            "bg" to ("Графично разпределение" to "Визуализирате статистическото разпределение на транзакциите по категории за различни периоди (година, месец, седмица, ден)"),
            //"ca"    to ("Gràfic de sectors" to ""),
            //"cs-CZ" to ("Koláčový graf" to ""),
            //"da-DK" to ("" to ""),
            "de-DE" to ("Kreisdiagramm" to "Visualisieren Sie die statistische Verteilung der Buchungen pro Kategorie über verschiedene Perioden (Jahre, Monate, Wochen, Tage)"),
            //"el-gr" to ("Γράφημα πίτας" to ""),
            "es-ES" to ("Gráfico de sectores" to "Visualice la distribución estadística de transacciones por categoría entre diferentes periodos (años, meses, semanas, días)"),
            "eu-ES" to ("Tarta diagrama" to "ikusi transakzioen banaketa estatistikoa kategoria bakoitzeko aldi desberdinetan zehar (urteak, hilabeteak, asteak, egunak)"),
            "fr-FR" to ("Diagramme circulaire" to "Visualisez la distribution statistique des opérations par catégorie sur différentes périodes (années, mois, semaines, jours)"),
            "hr" to ("Kružni dijagram" to "Vizualiziraj statističku raspodjelu transakcija po kategorijama kroz različita razdoblja (godine, mjeseci, tjedni, dani)"),
            "hu-HU" to ("Tortadiagram" to "A tranzakciók kategóriánkénti statisztikai eloszlásának szemléltetése különböző időszakokban (évek, hónapok, hetek, napok)"),
            "it-IT" to ("Grafico a torta" to "Visualizza la distribuzione statistica delle transazioni per categoria su periodi diversi (anni, mesi, settimane, giorni)"),
            "iw-IL" to ("תרשים עוגה" to "דמיינו את ההתפלגות הסטטיסטית של עסקאות לקטגוריה על פני תקופות שונות (שנים, חודשים, שבועות, ימים)"),
            "ja-JP" to ("円グラフ" to "異なる期間にわたる分類ごとの取引の統計的分布を視覚化します (年、月、週、日)"),
            //"km-KH" to ("" to ""),
            "kn-IN" to ("ಪೈ ಚಾರ್ಟ್" to "ವಿವಿಧ ಅವಧಿಗಳಲ್ಲಿ (ವರ್ಷಗಳು, ತಿಂಗಳುಗಳು, ವಾರಗಳು, ದಿನಗಳು) ಪ್ರತಿ ವರ್ಗದ ವಹಿವಾಟಿನ ಸಂಖ್ಯಾಶಾಸ್ತ್ರೀಯ ವಿತರಣೆಯನ್ನು ದೃಶ್ಯೀಕರಿಸಿ"),
            //"ko-KR" to ("" to ""),
            "ms" to ("Carta pai" to "Visualikan agihan berstatistik bagi transaksi per kategori mengikut tempoh berbeza (tahun, bulan, minggu atau hari)"),
            "pl-PL" to ("Wykres kołowy" to "Wizualizuj rozkład statystyczny transakcji według kategorii w różnych okresach (lata, miesiące, tygodnie, dni)"),
            "pt-PT" to ("Gráfico de pizza" to "visualize a distribuição estatística de transações por categoria em diferentes períodos (anos, meses, semanas, dias)"),
            //"ro"    to ("" to ""),
            "ru-RU" to ("Круговая диаграмма" to "визуализация статистического распределения транзакций по категориям за разные периоды (годы, месяцы, недели, дни)"),
            //"si-LK" to ("" to ""),
            "ta-IN" to ("பை விளக்கப்படம்" to "வெவ்வேறு காலகட்டங்களில் (ஆண்டுகள், மாதங்கள்,  வாரங்கள், நாட்கள்) ஒவ்வொரு வகையின் பரிவர்த்தனைகளின் புள்ளிவிவர பகிர்வை காட்சிப்படுத்தலாம்"),
            "te-IN" to ("పై చార్ట్" to "వివిధ కాలాలలో (సంవత్సరాలు, నెలలు, వారాలు, రోజులు) ప్రతి వర్గానికి లావాదేవీల గణాంక పంపిణీని దృశ్యమానం చేయండి"),
            "tr-TR" to ("Dilim grafik" to "değişik zaman dilimleri (sene, ay, hafta, gün) ile kategori başına muamelelerin istatistiki dağılımını görüntüleyin"),
            //"vi"    to ("Biểu đồ quạt" to ""),
            "zh-CN" to ("圆饼统计图" to "可视化不同时期（年、月、周、天）中，每个类别的交易统计分布"),
            "zh-TW" to ("圓餅統計圖" to "可視化不同時期（年、月、週、天）中，每個類別的交易統計分佈")
        )

        private val SKU_adfree = "adfree" to mapOf(
            "en-US" to ("No ads" to "No ads."),
            "ar" to ("بدون إعلانات" to "بدون إعلانات."),
            "bg" to ("Без реклами" to "Без реклами."),
            "ca" to ("Sense anuncis" to "Sense anuncis."),
            "cs-CZ" to ("Bez reklam" to "Bez reklam."),
            "da-DK" to ("Ingen annoncer" to "Ingen annoncer."),
            "de-DE" to ("Ohne Werbung" to "Ohne Werbung."),
            "el-gr" to ("Χωρίς διαφημίσεις" to "Χωρίς διαφημίσεις."),
            "es-ES" to ("Sin anuncios" to "Sin anuncios."),
            "eu-ES" to ("Iragarkirik gabe" to "Iragarkirik gabe."),
            "fr-FR" to ("Sans publicité" to "Sans publicité."),
            "hr" to ("Bez oglasa" to "Bez oglasa."),
            "hu-HU" to ("Hirdetések kikapcsolása" to "Hirdetések kikapcsolása."),
            "it-IT" to ("Nessun annuncio" to "Nessun annuncio."),
            "iw-IL" to ("ללא פרסומות" to "ללא פרסומות."),
            "ja-JP" to ("広告なし" to "広告なし。"),
//"km-KH" to"" to "."),
            "kn-IN" to ("ಜಾಹೀರಾತುಗಳಿಲ್ಲ" to "ಜಾಹೀರಾತುಗಳಿಲ್ಲ."),
            "ko-KR" to ("광고 없음" to "광고 없음."),
            "ms" to ("Tanpa iklan" to "Tanpa iklan."),
            "pl-PL" to ("Brak reklam" to "Brak reklam."),
            "pt-PT" to ("Sem anúncios" to "Sem anúncios."),
            "ro" to ("Fără reclame" to "Fără reclame."),
            "ru-RU" to ("Без рекламы" to "Без рекламы."),
//"si-LK" to"" to "."),
            "ta-IN" to ("விளம்பரங்கள் இல்லை" to "விளம்பரங்கள் இல்லை."),
            "te-IN" to ("ప్రకటనలు లేవు" to "ప్రకటనలు లేవు."),
            "tr-TR" to ("Reklamsız" to "Reklamsız."),
            "vi" to ("Không quảng cáo" to "Không quảng cáo."),
            "zh-CN" to ("无广告" to "无广告。"),
            "zh-TW" to ("無廣告" to "無廣告。")
        )
        private val SKU_accounts_unlimited = "accountsunlimited" to mapOf(
            "en-US" to ("Unlimited number of accounts" to "Unlock the ability to create more than five accounts."),
            "ar"    to ("عدد حسابات غير محدود" to "فتح القدرة على إنشاء أكثر من خمسة حسابات."),
            "bg"    to ("Неограничен брой сметки" to "Отключване на възможността за създаване на повече от пет акаунта."),
            "ca"    to ("Nombre il·limitat de comptes" to "Desbloqueja la possiblitat de crear més de cinc comptes."),
            "cs-CZ" to ("Neomezený počet účtů" to "Odemknutí možnosti vytvořit více než pět účtů."),
            "da-DK" to ("Ubegrænset antal konti" to "Lås op for muligheden for at oprette mere end fem konti."),
            "de-DE" to ("Unbegrenzte Anzahl von Konten" to "Schalten Sie die Möglichkeit frei, mehr als fünf Konten zu erstellen."),
            "el-gr" to ("Απεριόριστος αριθμός λογαριασμών" to "Ξεκλειδώστε τη δυνατότητα δημιουργίας περισσότερων από πέντε λογαριασμών."),
            "es-ES" to ("Número de cuentas ilimitadas" to "Desbloquear la capacidad de crear más de cinco cuentas."),
            "eu-ES" to ("Kontu kopuru mugagabea" to "Bost kontu baino gehiago sortzeko gaitasuna desblokeatu du."),
            "fr-FR" to ("Nombre illimité de comptes" to "Déverrouiller la possibilité de créer plus de cinq comptes."),
            "hr"    to ("Neograničen broj računa" to "Otključavanje mogućnosti stvaranja više od pet računa."),
            "hu-HU" to ("Korlátlan számú számla" to "Több mint öt fiók létrehozásának feloldása."),
            "it-IT" to ("Numero illimitato di conti" to "Sbloccare la possibilità di creare più di cinque conti."),
            "iw-IL" to ("מספר חשבונות בלתי מוגבל" to "נפתחה האפשרות ליצור יותר מחמישה חשבונות."),
            "ja-JP" to ("無制限の勘定数" to "5つ以上のアカウントを作成する機能のアンロック"),
            "km-KH" to ("ចំនួន​គណនី​គ្មាន​ដែន​កំណត់" to "ដោះសោ សមត្ថភាព បង្កើត គណនី ជាង ៥."),
            "kn-IN" to ("ಅನಿಯಮಿತ ಸಂಖ್ಯೆಯ ಖಾತೆಗಳು" to "ಐದಕ್ಕಿಂತ ಹೆಚ್ಚು ಖಾತೆಗಳನ್ನು ರಚಿಸುವ ಸಾಮರ್ಥ್ಯವನ್ನು ಅನ್ಲಾಕ್ ಮಾಡಿ."),
            "ko-KR" to ("개수 제한 없는 계좌" to "다섯 개 이상의 계정을 만들 수 있는 기능 잠금 해제."),
            "ms"    to ("Bilangan akaun tidak terhad" to "Buka kunci keupayaan untuk membuat lebih daripada lima akaun."),
            "pl-PL" to ("Nieskończona ilość kont" to "Odblokowanie możliwości tworzenia więcej niż pięciu kont."),
            "pt-PT" to ("Número ilimitado de contas" to "Desbloquear a capacidade de criar mais de cinco contas."),
            "ro"    to ("Număr nelimitat de conturi" to "Deblocați posibilitatea de a crea mai mult de cinci conturi."),
            "ru-RU" to ("Неограниченное количество счетов" to "Разблокировать возможность создания более пяти учетных записей."),
            "si-LK" to ("සීමා රහිත ගිණුම් ගණනක්" to "ගිණුම් පහකට වඩා සෑදීමේ හැකියාව අගුළු හරින්න."),
            "ta-IN" to ("கணக்குகளுக்கு வரம்பில்லை" to "ஐந்துக்கும் மேற்பட்ட கணக்குகளை உருவாக்கும் திறனைத் திறக்கவும்."),
            "te-IN" to ("అపరిమిత సంఖ్యలో ఖాతాలు" to "ఐదు కంటే ఎక్కువ ఖాతాలను సృష్టించగల సామర్థ్యాన్ని అన్‌లాక్ చేయండిఐదు కంటే ఎక్కువ ఖాతాలను సృష్టించగల సామర్థ్యాన్ని అన్‌లాక్ చేయండి."),
            "tr-TR" to ("Sınırsız sayıda hesap" to "Beşten fazla hesap oluşturma özelliğinin kilidini açın."),
            "vi"    to ("Số lượng không giới hạn các tài khoản" to "Mở khóa khả năng tạo nhiều hơn năm tài khoản."),
            "zh-CN" to ("无限数量的账户" to "解锁创建多于五个账户的能力"),
            "zh-TW" to ("無限數量的帳戶" to "解锁创建五个以上账户的能力")
        )

        //@formatter:on

        const val SERVICE_ACCOUNT_EMAIL =
            "fastlane@api-5950718857839288276-239934.iam.gserviceaccount.com"

        private val log: Log = LogFactory.getLog(Main::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            val sku = SKU_accounts_unlimited
            try {
                // Create the API service.
                val service: AndroidPublisher = AndroidPublisherHelper.init()
                val inappproducts = service.inappproducts()

                val content = InAppProduct()
                    .setPackageName(PACKAGE_NAME)
                    .setSku(sku.first)
                    .setPurchaseType("managedUser")
                    .setListings(
                        buildMap {
                            sku.second.forEach {
                                put(
                                    it.key,
                                    InAppProductListing().setTitle(it.value.first)
                                        .setDescription(it.value.second)
                                )
                            }
                        }
                    )

                inappproducts.patch(PACKAGE_NAME, sku.first, content).execute()

            } catch (ex: IOException) {
                log.error("Exception was thrown while updating listing", ex)
            } catch (ex: GeneralSecurityException) {
                log.error("Exception was thrown while updating listing", ex)
            }
        }
    }
}