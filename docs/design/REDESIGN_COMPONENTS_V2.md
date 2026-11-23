# Anti PDF — каталог компонентів редизайну V2

> Компоненти описані як контракт поведінки, а не як прив’язка до конкретного XML. Реалізація — Material 3 / Android Views, без нової UI-бібліотеки.

## 1. Навігаційні компоненти

### `AntiTopBar`

Варіанти:

- `HomeLibrary`: Anti PDF + Search + Filter/Sort + Settings;
- `HomeTools`: Інструменти + Settings, без бібліотечних команд;
- `Detail`: Back + Title + optional primary action + Overflow;
- `Edit`: Cancel/Back + filename/unsaved state + Save.

Правила: висота формується контентом та top inset; назва файла скорочується, але має повне accessibility name; overflow не містить дубль основної видимої дії.

### `AntiNavigation`

- Compact: Material bottom navigation з Library/Tools.
- Medium/Expanded: navigation rail.
- Вибір вкладки зберігається при recreate.
- Banner не є частиною navigation container і не змінює її touch bounds.

### `ReaderHud`

Три 48 dp зони: Page, Zoom, Search. При читанні може ховатися; при edit/error/search не дублює відповідні controls. Зміна сторінки не озвучується безперервно під час fling.

## 2. Основні дії

### `OpenDocumentFabCluster`

- Extended FAB **Відкрити PDF**;
- FAB **Ще дії**;
- secondary actions **Створити PDF**, **Сканувати** з текстовими labels;
- Back спершу закриває кластер;
- при відкритті background стає недоступним для TalkBack, фокус переходить на першу secondary action, після закриття повертається на opener;
- завжди вище banner/navigation/gesture inset.

### `StickyPrimaryAction`

Єдина домінантна кнопка робочого екрана: 52–56 dp, назва конкретної операції, loading state блокує повторний запуск. Розміщується над `max(navigationBars, IME)` і не перекриває content.

## 3. Контентні компоненти

### `RecentDocumentRow`

Мініатюра, назва до двох рядків, дата/розмір, optional progress, favorite, overflow. Весь рядок відкриває документ; вкладені дії мають власні 48 dp targets і не запускають row click.

### `ContinueReadingCard`

Показується лише за реального progress. Містить одну дію **Продовжити**. У списку нижче той самий файл може лишатися, але картка візуально відділяється як поточна задача.

### `ToolGroup`

Заголовок групи + список `ToolRow`/сітка. Порядок груп стабільний:

1. Create;
2. Pages;
3. Enhance & Optimize.

### `ToolRow`

48 dp icon container, title, description до двох рядків, chevron. Рядок має єдину action semantics. Disabled state використовується лише коли користувач розуміє, як його активувати; інакше дія приховується.

### `EmptyState`

Ілюстрація/іконка, title, short body, максимум одна локальна primary action. Не дублює глобальну дію без потреби. Варіанти: no recents, no search result, load error, no form fields.

## 4. Робочі операції

### `OperationStepHeader`

Показує Input → Options → Save; це не жорсткий wizard, якщо операція проста. Поточний крок має текст і selected state, а не лише колір.

### `SelectedFileRow`

Назва, тип/розмір за наявності, drag handle для ordered input, Remove. Для доступності має Move up/Move down actions; drag не є єдиним шляхом.

### `OptionSection`

Title + helper + controls + inline validation. Не ховає критичну умову в tooltip. Range input має приклад локалізованого формату.

### `OperationProgress`

Назва дії, determinate progress коли кроки відомі, neutral indeterminate інакше, optional Cancel лише якщо операція безпечно скасовується. Back не запускає дію вдруге.

### `OperationResult`

Result icon, filename, path/location у безпечному людському вигляді, size saving за наявності, actions Open / Share / Done. Interstitial може бути лише між фактичним успіхом і цим результатом та ніколи не впливає на файл.

## 5. Читач і редактор

### `ReaderModeFab`

Extended FAB **Редагувати**, visible лише коли доступ перевірено й reader не в error/edit/search conflict. Якщо document read-only, замість прихованої відмови показується пояснювальний state.

### `EditToolBar`

Compact: Text / Draw / Insert / Select / Undo / More. Expanded: може окремо показати Redo та Options. Кожен слот ≥48 dp; active state має background + state description. Delete з’являється лише в контексті selection.

### `ToolOptionsCard`

Bottom-anchored floating card, max width 420 dp. Містить лише параметри активного tool; заголовок і Close. Повторний tap active tool відкриває цю саму card. Card враховує IME/navigation inset і не перекриває selected object без можливості закрити.

### `SelectionActionPill`

Зберігає прийняту поведінку: над об’єктом, інакше під, інакше притискається до viewport; ховається під час gesture. Дії залежать від типу й мають 48 dp targets. Для TalkBack той самий набір доступний через virtual object node або object list, а не лише координатну pill.

### `AccessibleObjectList`

Альтернативне представлення markup objects для TalkBack/Switch Access: type, short content, page, selected state; actions Select/Edit/Delete/Move/Resize. Синхронізується з canvas selection і не створює другого джерела стану.

### `UnsavedState`

Короткий label у top bar та стандартний exit dialog: Save / Discard / Stay. Не використовує лише крапку чи колір.

### `FormCallout`

З’являється після виявлення полів: **Заповнити форму · N полів**. Не перекриває document; має dismissal і залишається доступним у overflow. Read-only reason пояснюється.

## 6. Сторінки та сканування

### `PageThumbnailTile`

Thumbnail, page number, selected state, drag handle, overflow/rotate. Accessibility actions Move earlier/later, Rotate, Select, Delete. Skeleton має ті самі bounds, щоб grid не стрибав.

### `PageSelectionBar`

Кількість вибраних + Rotate/Delete/Done. Delete не дозволяє залишити zero-page document; action має explicit destructive confirmation або Undo залежно від можливості відновлення.

### `ScanResultList`

Page thumbnail list, reorder, rotate, rescan/delete, filename. Camera permission і scanner availability пояснюються в момент дії; fallback не виглядає як помилка користувача.

## 7. Зворотний зв’язок

### `AntiSnackbar`

Короткі оборотні результати: Saved, Removed + Undo. До 2 рядків у звичайному масштабі, але не обрізає критичний action при 200%; якщо не вміщується — інша surface.

### `BlockingErrorState`

Title, human explanation, Retry, optional Choose another/Close. При появі accessibility focus переходить на title. Технічні exception/URI не показуються.

### `InlineProblem`

Поряд із control: icon + concise text + recovery. Поле має error state і зв’язок із повідомленням для screen reader.

### `ConfirmDestructiveDialog`

Називає об’єкт і наслідок; primary safe action, explicit destructive action, Cancel. Не використовує подвійних заперечень.

## 8. Legal, consent і реклама

### `LicenseList` / `LicenseDetail`

Власні AppCompat/Material screens із search optional, library name, license name, full license text; proper insets, Back, 200% font, TalkBack. Дані — з існуючого OSS generation/assets; юридичний текст не переписується.

### `BannerSlot`

Живе лише на дозволених main/tool surfaces, має зарезервовану безпечну область і стає `GONE` при no-consent/fail. Не входить у focus order застосунку як звичайна primary action і не стоїть впритул до FAB/CTA.

UMP не загортається у власний styled component: застосунок лише коректно запускає SDK flow і надає privacy options, коли SDK цього вимагає.

## 9. Обов’язкові стани кожного компонента

| Категорія | Стани |
|---|---|
| Action | enabled, pressed, focused, selected, disabled, loading |
| Content | loading, content, empty, no results, recoverable error, blocking error |
| Input | empty, filled, focused, invalid, disabled, read-only |
| Async | idle, running, success, error, cancelled/restored |
| Accessibility | normal, TalkBack focus, keyboard focus, large font, reduced motion |

## 10. Заборонені реалізації

- окремий custom control без 48 dp bounds, semantics і keyboard action;
- критична дія тільки через drag, pinch, long-press або swipe;
- fixed-height text container, який обрізається на 150–200%;
- hardcoded top/bottom padding замість WindowInsets;
- бібліотечні menu items на Tools tab;
- дубль однієї команди у видимому toolbar і sheet без різних контекстів;
- banner у reader/editor/organizer або поруч із primary save;
- Toast як єдине повідомлення про критичну помилку;
- новий локальний стан у View, який дублює ViewModel/контролер.
