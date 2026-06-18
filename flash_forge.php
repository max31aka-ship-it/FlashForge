<?php
// flash_forge.php - Флеш-карточки на PHP (CLI + веб)
// CLI: php flash_forge.php add --question="Вопрос" --answer="Ответ" --category="Наука"

$dataFile = 'flashcards.json';

class FlashCard {
    public $id;
    public $question;
    public $answer;
    public $category;
    public $box;
    public $nextReview;
    public $created;
    public $correctCount;
    public $wrongCount;

    function __construct($id, $q, $a, $cat, $box, $next, $created, $c, $w) {
        $this->id = $id;
        $this->question = $q;
        $this->answer = $a;
        $this->category = $cat;
        $this->box = $box;
        $this->nextReview = $next;
        $this->created = $created;
        $this->correctCount = $c;
        $this->wrongCount = $w;
    }
}

function loadData() {
    global $dataFile;
    if (file_exists($dataFile)) {
        $json = file_get_contents($dataFile);
        $data = json_decode($json, true);
        if ($data) {
            $cards = [];
            foreach ($data['cards'] as $c) {
                $cards[] = new FlashCard($c['id'], $c['question'], $c['answer'], $c['category'], $c['box'],
                                         $c['nextReview'], $c['created'], $c['correctCount'], $c['wrongCount']);
            }
            return ['cards' => $cards, 'next_id' => $data['next_id']];
        }
    }
    return ['cards' => [], 'next_id' => 1];
}

function saveData($data) {
    global $dataFile;
    $json = ['cards' => [], 'next_id' => $data['next_id']];
    foreach ($data['cards'] as $c) {
        $json['cards'][] = [
            'id' => $c->id, 'question' => $c->question, 'answer' => $c->answer,
            'category' => $c->category, 'box' => $c->box, 'nextReview' => $c->nextReview,
            'created' => $c->created, 'correctCount' => $c->correctCount, 'wrongCount' => $c->wrongCount
        ];
    }
    file_put_contents($dataFile, json_encode($json, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));
}

function addCard(&$data, $question, $answer, $category) {
    if (!$category) $category = 'General';
    $card = new FlashCard($data['next_id']++, $question, $answer, $category, 1,
                          date('Y-m-d'), date('c'), 0, 0);
    $data['cards'][] = $card;
    saveData($data);
    return $card;
}

function editCard(&$data, $id, $question, $answer, $category) {
    foreach ($data['cards'] as &$c) {
        if ($c->id == $id) {
            if ($question !== null) $c->question = $question;
            if ($answer !== null) $c->answer = $answer;
            if ($category !== null) $c->category = $category;
            saveData($data);
            return true;
        }
    }
    return false;
}

function deleteCard(&$data, $id) {
    $filtered = array_filter($data['cards'], function($c) use ($id) { return $c->id != $id; });
    if (count($filtered) < count($data['cards'])) {
        $data['cards'] = array_values($filtered);
        saveData($data);
        return true;
    }
    return false;
}

function getCardsForReview($data, $count = 10) {
    $today = date('Y-m-d');
    $ready = array_filter($data['cards'], function($c) use ($today) { return $c->nextReview <= $today; });
    $ready = array_values($ready);
    usort($ready, function($a, $b) { return $a->box - $b->box; });
    shuffle($ready);
    return array_slice($ready, 0, min($count, count($ready)));
}

function updateCardAfterReview(&$data, $id, $correct) {
    foreach ($data['cards'] as &$c) {
        if ($c->id == $id) {
            if ($correct) {
                $c->correctCount++;
                $c->box = min($c->box + 1, 5);
                $intervals = [1, 3, 7, 14, 30];
                $c->nextReview = date('Y-m-d', strtotime("+{$intervals[$c->box-1]} days"));
            } else {
                $c->wrongCount++;
                $c->box = 1;
                $c->nextReview = date('Y-m-d');
            }
            saveData($data);
            return;
        }
    }
}

function getStatistics($data) {
    $total = count($data['cards']);
    if ($total == 0) return ['total' => 0, 'correct' => 0, 'wrong' => 0, 'avgCorrect' => 0, 'byBox' => [1=>0,2=>0,3=>0,4=>0,5=>0]];
    $correct = array_sum(array_map(function($c) { return $c->correctCount; }, $data['cards']));
    $wrong = array_sum(array_map(function($c) { return $c->wrongCount; }, $data['cards']));
    $avgCorrect = $total > 0 ? ($correct / $total * 100) : 0;
    $byBox = [1=>0,2=>0,3=>0,4=>0,5=>0];
    foreach ($data['cards'] as $c) $byBox[$c->box]++;
    return ['total' => $total, 'correct' => $correct, 'wrong' => $wrong, 'avgCorrect' => $avgCorrect, 'byBox' => $byBox];
}

function getCategories($data) {
    $cats = array_unique(array_map(function($c) { return $c->category; }, $data['cards']));
    sort($cats);
    return $cats;
}

function exportCSV($data, $filepath) {
    $f = fopen($filepath, 'w');
    fputcsv($f, ['ID', 'Question', 'Answer', 'Category', 'Box', 'NextReview', 'Created', 'Correct', 'Wrong']);
    foreach ($data['cards'] as $c) {
        fputcsv($f, [$c->id, $c->question, $c->answer, $c->category, $c->box, $c->nextReview, $c->created, $c->correctCount, $c->wrongCount]);
    }
    fclose($f);
}

function importCSV(&$data, $filepath) {
    $f = fopen($filepath, 'r');
    $header = fgetcsv($f);
    while (($row = fgetcsv($f)) !== false) {
        if (count($row) < 9) continue;
        $q = $row[1];
        $a = $row[2];
        $cat = $row[3] ?: 'General';
        addCard($data, $q, $a, $cat);
    }
    fclose($f);
}

// ========== CLI ==========
if (php_sapi_name() === 'cli') {
    $options = getopt("", ["cmd:", "question:", "answer:", "category:", "id:", "count:", "output:", "file:", "new-question:", "new-answer:", "new-category:"]);
    $cmd = $options['cmd'] ?? null;
    $data = loadData();

    switch ($cmd) {
        case 'add':
            $q = $options['question'] ?? '';
            $a = $options['answer'] ?? '';
            $cat = $options['category'] ?? 'General';
            if (!$q || !$a) { echo "Укажите --question и --answer\n"; break; }
            $card = addCard($data, $q, $a, $cat);
            echo "✅ Карточка #{$card->id} добавлена\n";
            break;
        case 'list':
            $cat = $options['category'] ?? null;
            $cards = $cat ? array_filter($data['cards'], function($c) use ($cat) { return $c->category == $cat; }) : $data['cards'];
            if (empty($cards)) { echo "Нет карточек.\n"; break; }
            printf("%-4s %-30s %-30s %-15s %-8s\n", "ID", "Вопрос", "Ответ", "Категория", "Коробка");
            foreach ($cards as $c) {
                printf("%-4d %-30s %-30s %-15s %-8d\n", $c->id, substr($c->question, 0, 28), substr($c->answer, 0, 28), $c->category, $c->box);
            }
            break;
        case 'study':
            $count = isset($options['count']) ? (int)$options['count'] : 10;
            $cards = getCardsForReview($data, $count);
            if (empty($cards)) { echo "Нет карточек для повторения.\n"; break; }
            echo "\n🧠 Начинаем изучение! " . count($cards) . " карточек.\n\n";
            foreach ($cards as $i => $c) {
                echo "[" . ($i+1) . "/" . count($cards) . "] Вопрос: {$c->question}\n";
                echo "Нажмите Enter, чтобы увидеть ответ...";
                fgets(STDIN);
                echo "Ответ: {$c->answer}\n";
                while (true) {
                    echo "Правильно? (y/n): ";
                    $ans = trim(fgets(STDIN));
                    if ($ans == 'y' || $ans == 'n') {
                        updateCardAfterReview($data, $c->id, $ans == 'y');
                        break;
                    }
                    echo "Введите y или n\n";
                }
                echo "\n";
            }
            echo "🎯 Изучение завершено!\n";
            break;
        case 'stats':
            $stats = getStatistics($data);
            echo "📊 СТАТИСТИКА\n";
            echo "Всего карточек: {$stats['total']}\n";
            echo "Правильных ответов: {$stats['correct']}\n";
            echo "Неправильных ответов: {$stats['wrong']}\n";
            echo "Средняя правильность: " . number_format($stats['avgCorrect'], 2) . "%\n";
            echo "Распределение по коробкам (Лейтнер):\n";
            for ($i = 1; $i <= 5; $i++) {
                echo "  Коробка $i: {$stats['byBox'][$i]} карточек\n";
            }
            break;
        case 'edit':
            $id = isset($options['id']) ? (int)$options['id'] : 0;
            $q = $options['new-question'] ?? null;
            $a = $options['new-answer'] ?? null;
            $cat = $options['new-category'] ?? null;
            if (!$id) { echo "Укажите --id\n"; break; }
            if (editCard($data, $id, $q, $a, $cat)) {
                echo "✅ Карточка #$id обновлена\n";
            } else {
                echo "❌ Карточка #$id не найдена\n";
            }
            break;
        case 'delete':
            $id = isset($options['id']) ? (int)$options['id'] : 0;
            if (!$id) { echo "Укажите --id\n"; break; }
            if (deleteCard($data, $id)) {
                echo "✅ Карточка #$id удалена\n";
            } else {
                echo "❌ Карточка #$id не найдена\n";
            }
            break;
        case 'export':
            $output = $options['output'] ?? null;
            if (!$output) { echo "Укажите --output\n"; break; }
            exportCSV($data, $output);
            echo "Экспортировано в $output\n";
            break;
        case 'import':
            $file = $options['file'] ?? null;
            if (!$file) { echo "Укажите --file\n"; break; }
            importCSV($data, $file);
            echo "Импортировано из $file\n";
            break;
        default:
            interactiveMode($data);
            break;
    }
    exit;
}

// ========== ИНТЕРАКТИВНЫЙ РЕЖИМ ==========
function interactiveMode(&$data) {
    while (true) {
        echo "\n🧠 FlashForge - Флеш-карточки (интерактивный)\n";
        echo "1. Добавить карточку\n";
        echo "2. Список карточек\n";
        echo "3. Начать изучение\n";
        echo "4. Статистика\n";
        echo "5. Редактировать\n";
        echo "6. Удалить\n";
        echo "7. Экспорт CSV\n";
        echo "8. Импорт CSV\n";
        echo "0. Выход\n";
        echo "Выберите действие: ";
        $choice = trim(fgets(STDIN));
        switch ($choice) {
            case '0': return;
            case '1':
                echo "Вопрос: ";
                $q = trim(fgets(STDIN));
                if (!$q) { echo "Вопрос обязателен\n"; break; }
                echo "Ответ: ";
                $a = trim(fgets(STDIN));
                if (!$a) { echo "Ответ обязателен\n"; break; }
                echo "Категория (по умолчанию General): ";
                $cat = trim(fgets(STDIN));
                if (!$cat) $cat = 'General';
                $card = addCard($data, $q, $a, $cat);
                echo "✅ Карточка #{$card->id} добавлена\n";
                break;
            case '2':
                echo "Категория (Enter все): ";
                $cat = trim(fgets(STDIN));
                $cards = $cat ? array_filter($data['cards'], function($c) use ($cat) { return $c->category == $cat; }) : $data['cards'];
                if (empty($cards)) { echo "Нет карточек.\n"; break; }
                printf("%-4s %-30s %-30s %-15s %-8s\n", "ID", "Вопрос", "Ответ", "Категория", "Коробка");
                foreach ($cards as $c) {
                    printf("%-4d %-30s %-30s %-15s %-8d\n", $c->id, substr($c->question, 0, 28), substr($c->answer, 0, 28), $c->category, $c->box);
                }
                break;
            case '3':
                echo "Количество карточек (по умолчанию 10): ";
                $cntStr = trim(fgets(STDIN));
                $count = $cntStr ? (int)$cntStr : 10;
                $cards = getCardsForReview($data, $count);
                if (empty($cards)) { echo "Нет карточек для повторения.\n"; break; }
                echo "\n🧠 Начинаем изучение! " . count($cards) . " карточек.\n\n";
                foreach ($cards as $i => $c) {
                    echo "[" . ($i+1) . "/" . count($cards) . "] Вопрос: {$c->question}\n";
                    echo "Нажмите Enter, чтобы увидеть ответ...";
                    fgets(STDIN);
                    echo "Ответ: {$c->answer}\n";
                    while (true) {
                        echo "Правильно? (y/n): ";
                        $ans = trim(fgets(STDIN));
                        if ($ans == 'y' || $ans == 'n') {
                            updateCardAfterReview($data, $c->id, $ans == 'y');
                            break;
                        }
                        echo "Введите y или n\n";
                    }
                    echo "\n";
                }
                echo "🎯 Изучение завершено!\n";
                break;
            case '4':
                $stats = getStatistics($data);
                echo "📊 СТАТИСТИКА\n";
                echo "Всего карточек: {$stats['total']}\n";
                echo "Правильных ответов: {$stats['correct']}\n";
                echo "Неправильных ответов: {$stats['wrong']}\n";
                echo "Средняя правильность: " . number_format($stats['avgCorrect'], 2) . "%\n";
                echo "Распределение по коробкам (Лейтнер):\n";
                for ($i = 1; $i <= 5; $i++) {
                    echo "  Коробка $i: {$stats['byBox'][$i]} карточек\n";
                }
                break;
            case '5':
                echo "ID карточки: ";
                $id = (int)trim(fgets(STDIN));
                $card = null;
                foreach ($data['cards'] as $c) if ($c->id == $id) { $card = $c; break; }
                if (!$card) { echo "Карточка не найдена\n"; break; }
                echo "Оставьте пустым, чтобы не менять.\n";
                echo "Вопрос ({$card->question}): ";
                $q = trim(fgets(STDIN));
                if ($q === '') $q = null;
                echo "Ответ ({$card->answer}): ";
                $a = trim(fgets(STDIN));
                if ($a === '') $a = null;
                echo "Категория ({$card->category}): ";
                $cat = trim(fgets(STDIN));
                if ($cat === '') $cat = null;
                if (editCard($data, $id, $q, $a, $cat)) {
                    echo "✅ Обновлено\n";
                } else {
                    echo "❌ Ошибка\n";
                }
                break;
            case '6':
                echo "ID для удаления: ";
                $id = (int)trim(fgets(STDIN));
                if (deleteCard($data, $id)) {
                    echo "✅ Удалено\n";
                } else {
                    echo "❌ Не найдено\n";
                }
                break;
            case '7':
                echo "Имя файла (CSV): ";
                $file = trim(fgets(STDIN));
                if (!$file) $file = 'flashcards.csv';
                exportCSV($data, $file);
                echo "Экспортировано в $file\n";
                break;
            case '8':
                echo "Имя файла (CSV): ";
                $file = trim(fgets(STDIN));
                if (!$file) { echo "Укажите файл\n"; break; }
                importCSV($data, $file);
                echo "Импортировано из $file\n";
                break;
            default:
                echo "Неверный выбор\n";
        }
    }
}

// ========== ВЕБ-ИНТЕРФЕЙС ==========
if (php_sapi_name() !== 'cli') {
    $data = loadData();
    ?>
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <title>🧠 FlashForge - Флеш-карточки</title>
        <style>
            body { font-family: 'Segoe UI', sans-serif; background: #f4f7fb; margin: 20px; }
            .container { max-width: 800px; margin: 0 auto; background: white; padding: 20px; border-radius: 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
            table { width: 100%; border-collapse: collapse; margin-top: 20px; }
            th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
            th { background: #2c3e50; color: white; }
            .form-row { margin: 10px 0; }
            .form-row label { display: inline-block; width: 80px; }
            input, select, button { padding: 6px; margin: 2px; }
            button { background: #3498db; color: white; border: none; border-radius: 4px; cursor: pointer; }
            .stats { margin-top: 20px; background: #e8f5e9; padding: 10px; border-radius: 8px; }
        </style>
    </head>
    <body>
    <div class="container">
        <h1>🧠 FlashForge - Флеш-карточки</h1>
        <h3>Добавить карточку</h3>
        <form method="GET">
            <div class="form-row"><label>Вопрос:</label><input type="text" name="question" required></div>
            <div class="form-row"><label>Ответ:</label><input type="text" name="answer" required></div>
            <div class="form-row"><label>Категория:</label><input type="text" name="category" value="General"></div>
            <button type="submit" name="action" value="add">➕ Добавить</button>
        </form>

        <h3>Список карточек</h3>
        <table>
            <tr><th>ID</th><th>Вопрос</th><th>Ответ</th><th>Категория</th><th>Коробка</th></tr>
            <?php foreach ($data['cards'] as $c): ?>
                <tr>
                    <td><?= $c->id ?></td>
                    <td><?= htmlspecialchars($c->question) ?></td>
                    <td><?= htmlspecialchars($c->answer) ?></td>
                    <td><?= $c->category ?></td>
                    <td><?= $c->box ?></td>
                </tr>
            <?php endforeach; ?>
        </table>

        <?php
        if (isset($_GET['action']) && $_GET['action'] == 'add' && isset($_GET['question']) && isset($_GET['answer'])) {
            $q = $_GET['question'];
            $a = $_GET['answer'];
            $cat = $_GET['category'] ?? 'General';
            addCard($data, $q, $a, $cat);
            echo "<div style='background:#d5f5e3; padding:10px; margin-top:10px;'>✅ Добавлено</div>";
            header("Location: ?");
            exit;
        }

        // Изучение
        if (isset($_GET['action']) && $_GET['action'] == 'study') {
            $cards = getCardsForReview($data, 10);
            if (empty($cards)) {
                echo "<p>Нет карточек для повторения.</p>";
            } else {
                echo "<h3>🧠 Изучение</h3>";
                foreach ($cards as $i => $c) {
                    echo "<div style='border:1px solid #ddd; padding:10px; margin:10px 0; border-radius:8px;'>";
                    echo "<p><strong>Вопрос " . ($i+1) . "/" . count($cards) . ":</strong> " . htmlspecialchars($c->question) . "</p>";
                    echo "<button onclick=\"toggleAnswer(this)\">Показать ответ</button>";
                    echo "<p id='answer-{$c->id}' style='display:none;'><strong>Ответ:</strong> " . htmlspecialchars($c->answer) . "</p>";
                    echo "<div style='margin-top:10px;'>";
                    echo "<button onclick=\"answerCard({$c->id}, true)\">✅ Правильно</button>";
                    echo "<button onclick=\"answerCard({$c->id}, false)\">❌ Неправильно</button>";
                    echo "</div>";
                    echo "</div>";
                }
            }
        }

        // Статистика
        $stats = getStatistics($data);
        echo "<div class='stats'><h3>📊 Статистика</h3>";
        echo "<p>Всего карточек: {$stats['total']}</p>";
        echo "<p>Правильных ответов: {$stats['correct']}</p>";
        echo "<p>Неправильных ответов: {$stats['wrong']}</p>";
        echo "<p>Средняя правильность: " . number_format($stats['avgCorrect'], 2) . "%</p>";
        echo "<p>Распределение по коробкам:</p><ul>";
        for ($i = 1; $i <= 5; $i++) {
            echo "<li>Коробка $i: {$stats['byBox'][$i]} карточек</li>";
        }
        echo "</ul></div>";
        ?>
        <p>
            <a href="?action=study">🧠 Начать изучение</a> |
            <a href="?action=export">📤 Экспорт CSV</a>
        </p>
        <?php
        if (isset($_GET['action']) && $_GET['action'] == 'export') {
            exportCSV($data, 'flashcards.csv');
            echo "<div style='background:#d5f5e3; padding:10px;'>✅ Экспортировано в flashcards.csv</div>";
        }
        ?>
    </div>
    <script>
        function toggleAnswer(btn) {
            const div = btn.parentElement;
            const answerDiv = div.querySelector('[id^="answer-"]');
            if (answerDiv.style.display === 'none') {
                answerDiv.style.display = 'block';
                btn.textContent = 'Скрыть ответ';
            } else {
                answerDiv.style.display = 'none';
                btn.textContent = 'Показать ответ';
            }
        }
        function answerCard(id, correct) {
            // Для простоты используем GET-запрос (в реальном проекте AJAX)
            window.location.href = `?action=answer&id=${id}&correct=${correct ? 1 : 0}`;
        }
    </script>
    <?php
    // Обработка ответа
    if (isset($_GET['action']) && $_GET['action'] == 'answer' && isset($_GET['id']) && isset($_GET['correct'])) {
        $id = (int)$_GET['id'];
        $correct = $_GET['correct'] == 1;
        updateCardAfterReview($data, $id, $correct);
        header("Location: ?action=study");
        exit;
    }
}
