// FlashForge.java - Флеш-карточки на Java (CLI + Swing GUI)
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class FlashForge {
    private static final String DATA_FILE = "flashcards.json";

    static class FlashCard {
        int id; String question; String answer; String category; int box;
        String nextReview; String created; int correctCount; int wrongCount;
        FlashCard(int id, String q, String a, String cat, int box, String next, String created, int c, int w) {
            this.id = id; this.question = q; this.answer = a; this.category = cat; this.box = box;
            this.nextReview = next; this.created = created; this.correctCount = c; this.wrongCount = w;
        }
    }

    static class Forge {
        List<FlashCard> cards = new ArrayList<>();
        int nextId = 1;

        void load() {
            // Упрощённо: для реального проекта использовать Jackson
            // В этой версии загружаем дефолтные карточки
            if (cards.isEmpty()) {
                // Добавляем несколько примеров
                addCard("Столица Франции?", "Париж", "География");
                addCard("Сколько планет в Солнечной системе?", "8", "Наука");
                addCard("Кто написал 'Войну и мир'?", "Толстой", "Литература");
            }
        }

        void save() {
            try (PrintWriter pw = new PrintWriter(DATA_FILE)) {
                pw.println("[");
                for (int i = 0; i < cards.size(); i++) {
                    FlashCard c = cards.get(i);
                    pw.printf("  {\"id\":%d,\"question\":\"%s\",\"answer\":\"%s\",\"category\":\"%s\",\"box\":%d,\"nextReview\":\"%s\",\"created\":\"%s\",\"correctCount\":%d,\"wrongCount\":%d}%s\n",
                            c.id, c.question, c.answer, c.category, c.box, c.nextReview, c.created, c.correctCount, c.wrongCount,
                            (i < cards.size()-1 ? "," : ""));
                }
                pw.println("]");
            } catch (IOException e) {}
        }

        void addCard(String question, String answer, String category) {
            FlashCard c = new FlashCard(nextId++, question, answer, category, 1,
                    LocalDate.now().toString(), LocalDate.now().toString(), 0, 0);
            cards.add(c);
            save();
        }

        boolean editCard(int id, String question, String answer, String category) {
            for (FlashCard c : cards) {
                if (c.id == id) {
                    if (question != null) c.question = question;
                    if (answer != null) c.answer = answer;
                    if (category != null) c.category = category;
                    save();
                    return true;
                }
            }
            return false;
        }

        boolean deleteCard(int id) {
            for (Iterator<FlashCard> it = cards.iterator(); it.hasNext(); ) {
                if (it.next().id == id) {
                    it.remove();
                    save();
                    return true;
                }
            }
            return false;
        }

        List<FlashCard> getCardsForReview(int count) {
            String today = LocalDate.now().toString();
            List<FlashCard> ready = cards.stream().filter(c -> c.nextReview.compareTo(today) <= 0).collect(Collectors.toList());
            Collections.shuffle(ready);
            return ready.stream().limit(count).collect(Collectors.toList());
        }

        void updateCardAfterReview(int id, boolean correct) {
            for (FlashCard c : cards) {
                if (c.id == id) {
                    if (correct) {
                        c.correctCount++;
                        c.box = Math.min(c.box + 1, 5);
                        int[] intervals = {1, 3, 7, 14, 30};
                        LocalDate next = LocalDate.now().plusDays(intervals[c.box - 1]);
                        c.nextReview = next.toString();
                    } else {
                        c.wrongCount++;
                        c.box = 1;
                        c.nextReview = LocalDate.now().toString();
                    }
                    save();
                    return;
                }
            }
        }

        Map<String, Object> getStatistics() {
            int total = cards.size();
            int correct = cards.stream().mapToInt(c -> c.correctCount).sum();
            int wrong = cards.stream().mapToInt(c -> c.wrongCount).sum();
            double avgCorrect = total > 0 ? (double) correct / total * 100 : 0;
            Map<Integer, Integer> byBox = new HashMap<>();
            for (int i = 1; i <= 5; i++) byBox.put(i, 0);
            for (FlashCard c : cards) byBox.put(c.box, byBox.get(c.box) + 1);
            Map<String, Object> result = new HashMap<>();
            result.put("total", total); result.put("correct", correct); result.put("wrong", wrong);
            result.put("avgCorrect", avgCorrect); result.put("byBox", byBox);
            return result;
        }

        List<String> getCategories() {
            return cards.stream().map(c -> c.category).distinct().sorted().collect(Collectors.toList());
        }

        void exportCSV(String filepath) throws IOException {
            try (PrintWriter pw = new PrintWriter(filepath)) {
                pw.println("ID,Question,Answer,Category,Box,NextReview,Created,Correct,Wrong");
                for (FlashCard c : cards) {
                    pw.printf("%d,\"%s\",\"%s\",\"%s\",%d,%s,%s,%d,%d\n",
                            c.id, c.question, c.answer, c.category, c.box, c.nextReview, c.created, c.correctCount, c.wrongCount);
                }
            }
        }

        void importCSV(String filepath) throws IOException {
            try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
                String line = br.readLine(); // header
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 9) {
                        String q = parts[1].replaceAll("^\"|\"$", "");
                        String a = parts[2].replaceAll("^\"|\"$", "");
                        String cat = parts[3].replaceAll("^\"|\"$", "");
                        addCard(q, a, cat);
                    }
                }
            }
        }
    }

    // ========== CLI ==========
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--gui")) {
            SwingUtilities.invokeLater(() -> new FlashForgeGUI().setVisible(true));
            return;
        }
        Forge forge = new Forge();
        forge.load();
        if (args.length == 0) {
            interactiveMode(forge);
            return;
        }
        try {
            String cmd = args[0];
            switch (cmd) {
                case "add": {
                    String q = null, a = null, cat = "General";
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equals("--question")) q = args[++i];
                        else if (args[i].equals("--answer")) a = args[++i];
                        else if (args[i].equals("--category")) cat = args[++i];
                    }
                    if (q == null || a == null) { System.out.println("Укажите --question и --answer"); return; }
                    forge.addCard(q, a, cat);
                    System.out.println("✅ Карточка добавлена");
                    break;
                }
                case "list": {
                    String cat = null;
                    for (int i = 1; i < args.length; i++) if (args[i].equals("--category")) cat = args[++i];
                    List<FlashCard> cards = cat == null ? forge.cards :
                            forge.cards.stream().filter(c -> c.category.equals(cat)).collect(Collectors.toList());
                    if (cards.isEmpty()) { System.out.println("Нет карточек."); break; }
                    System.out.printf("%-4s %-30s %-30s %-15s %-8s\n", "ID", "Вопрос", "Ответ", "Категория", "Коробка");
                    for (FlashCard c : cards) {
                        System.out.printf("%-4d %-30s %-30s %-15s %-8d\n", c.id, c.question, c.answer, c.category, c.box);
                    }
                    break;
                }
                case "study": {
                    int count = 10;
                    for (int i = 1; i < args.length; i++) if (args[i].equals("--count")) count = Integer.parseInt(args[++i]);
                    List<FlashCard> cards = forge.getCardsForReview(count);
                    if (cards.isEmpty()) { System.out.println("Нет карточек для повторения."); break; }
                    System.out.printf("\n🧠 Начинаем изучение! %d карточек.\n\n", cards.size());
                    Scanner sc = new Scanner(System.in);
                    for (int i = 0; i < cards.size(); i++) {
                        FlashCard c = cards.get(i);
                        System.out.printf("[%d/%d] Вопрос: %s\n", i+1, cards.size(), c.question);
                        System.out.print("Нажмите Enter, чтобы увидеть ответ...");
                        sc.nextLine();
                        System.out.printf("Ответ: %s\n", c.answer);
                        while (true) {
                            System.out.print("Правильно? (y/n): ");
                            String ans = sc.nextLine().trim().toLowerCase();
                            if (ans.equals("y") || ans.equals("n")) {
                                forge.updateCardAfterReview(c.id, ans.equals("y"));
                                break;
                            }
                            System.out.println("Введите y или n");
                        }
                        System.out.println();
                    }
                    System.out.println("🎯 Изучение завершено!");
                    break;
                }
                case "stats": {
                    Map<String, Object> stats = forge.getStatistics();
                    System.out.println("📊 СТАТИСТИКА");
                    System.out.printf("Всего карточек: %d\n", stats.get("total"));
                    System.out.printf("Правильных ответов: %d\n", stats.get("correct"));
                    System.out.printf("Неправильных ответов: %d\n", stats.get("wrong"));
                    System.out.printf("Средняя правильность: %.2f%%\n", stats.get("avgCorrect"));
                    System.out.println("Распределение по коробкам (Лейтнер):");
                    Map<Integer, Integer> byBox = (Map<Integer, Integer>) stats.get("byBox");
                    for (int i = 1; i <= 5; i++) {
                        System.out.printf("  Коробка %d: %d карточек\n", i, byBox.get(i));
                    }
                    break;
                }
                case "edit": {
                    int id = 0; String q = null, a = null, cat = null;
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equals("--id")) id = Integer.parseInt(args[++i]);
                        else if (args[i].equals("--question")) q = args[++i];
                        else if (args[i].equals("--answer")) a = args[++i];
                        else if (args[i].equals("--category")) cat = args[++i];
                    }
                    if (id == 0) { System.out.println("Укажите --id"); return; }
                    if (forge.editCard(id, q, a, cat)) {
                        System.out.printf("✅ Карточка #%d обновлена\n", id);
                    } else {
                        System.out.printf("❌ Карточка #%d не найдена\n", id);
                    }
                    break;
                }
                case "delete": {
                    int id = 0;
                    for (int i = 1; i < args.length; i++) if (args[i].equals("--id")) id = Integer.parseInt(args[++i]);
                    if (id == 0) { System.out.println("Укажите --id"); return; }
                    if (forge.deleteCard(id)) {
                        System.out.printf("✅ Карточка #%d удалена\n", id);
                    } else {
                        System.out.printf("❌ Карточка #%d не найдена\n", id);
                    }
                    break;
                }
                case "export": {
                    String output = null;
                    for (int i = 1; i < args.length; i++) if (args[i].equals("--output")) output = args[++i];
                    if (output == null) { System.out.println("Укажите --output"); return; }
                    forge.exportCSV(output);
                    System.out.println("Экспортировано в " + output);
                    break;
                }
                case "import": {
                    String file = null;
                    for (int i = 1; i < args.length; i++) if (args[i].equals("--file")) file = args[++i];
                    if (file == null) { System.out.println("Укажите --file"); return; }
                    forge.importCSV(file);
                    System.out.println("Импортировано из " + file);
                    break;
                }
                default: interactiveMode(forge);
            }
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    static void interactiveMode(Forge forge) {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n🧠 FlashForge - Флеш-карточки (интерактивный)");
            System.out.println("1. Добавить карточку");
            System.out.println("2. Список карточек");
            System.out.println("3. Начать изучение");
            System.out.println("4. Статистика");
            System.out.println("5. Редактировать");
            System.out.println("6. Удалить");
            System.out.println("7. Экспорт CSV");
            System.out.println("8. Импорт CSV");
            System.out.println("0. Выход");
            System.out.print("Выберите действие: ");
            String choice = sc.nextLine();
            switch (choice) {
                case "0": return;
                case "1": {
                    System.out.print("Вопрос: ");
                    String q = sc.nextLine();
                    if (q.isEmpty()) { System.out.println("Вопрос обязателен"); break; }
                    System.out.print("Ответ: ");
                    String a = sc.nextLine();
                    if (a.isEmpty()) { System.out.println("Ответ обязателен"); break; }
                    System.out.print("Категория (по умолчанию General): ");
                    String cat = sc.nextLine();
                    if (cat.isEmpty()) cat = "General";
                    forge.addCard(q, a, cat);
                    System.out.println("✅ Карточка добавлена");
                    break;
                }
                case "2": {
                    System.out.print("Категория (Enter все): ");
                    String cat = sc.nextLine();
                    if (cat.isEmpty()) cat = null;
                    List<FlashCard> cards = cat == null ? forge.cards :
                            forge.cards.stream().filter(c -> c.category.equals(cat)).collect(Collectors.toList());
                    if (cards.isEmpty()) { System.out.println("Нет карточек."); break; }
                    System.out.printf("%-4s %-30s %-30s %-15s %-8s\n", "ID", "Вопрос", "Ответ", "Категория", "Коробка");
                    for (FlashCard c : cards) {
                        System.out.printf("%-4d %-30s %-30s %-15s %-8d\n", c.id, c.question, c.answer, c.category, c.box);
                    }
                    break;
                }
                case "3": {
                    System.out.print("Количество карточек (по умолчанию 10): ");
                    String cntStr = sc.nextLine();
                    int count = cntStr.isEmpty() ? 10 : Integer.parseInt(cntStr);
                    List<FlashCard> cards = forge.getCardsForReview(count);
                    if (cards.isEmpty()) { System.out.println("Нет карточек для повторения."); break; }
                    System.out.printf("\n🧠 Начинаем изучение! %d карточек.\n\n", cards.size());
                    for (int i = 0; i < cards.size(); i++) {
                        FlashCard c = cards.get(i);
                        System.out.printf("[%d/%d] Вопрос: %s\n", i+1, cards.size(), c.question);
                        System.out.print("Нажмите Enter, чтобы увидеть ответ...");
                        sc.nextLine();
                        System.out.printf("Ответ: %s\n", c.answer);
                        while (true) {
                            System.out.print("Правильно? (y/n): ");
                            String ans = sc.nextLine().trim().toLowerCase();
                            if (ans.equals("y") || ans.equals("n")) {
                                forge.updateCardAfterReview(c.id, ans.equals("y"));
                                break;
                            }
                            System.out.println("Введите y или n");
                        }
                        System.out.println();
                    }
                    System.out.println("🎯 Изучение завершено!");
                    break;
                }
                case "4": {
                    Map<String, Object> stats = forge.getStatistics();
                    System.out.println("📊 СТАТИСТИКА");
                    System.out.printf("Всего карточек: %d\n", stats.get("total"));
                    System.out.printf("Правильных ответов: %d\n", stats.get("correct"));
                    System.out.printf("Неправильных ответов: %d\n", stats.get("wrong"));
                    System.out.printf("Средняя правильность: %.2f%%\n", stats.get("avgCorrect"));
                    System.out.println("Распределение по коробкам (Лейтнер):");
                    Map<Integer, Integer> byBox = (Map<Integer, Integer>) stats.get("byBox");
                    for (int i = 1; i <= 5; i++) {
                        System.out.printf("  Коробка %d: %d карточек\n", i, byBox.get(i));
                    }
                    break;
                }
                case "5": {
                    System.out.print("ID карточки: ");
                    int id = Integer.parseInt(sc.nextLine());
                    FlashCard card = forge.cards.stream().filter(c -> c.id == id).findFirst().orElse(null);
                    if (card == null) { System.out.println("Карточка не найдена"); break; }
                    System.out.println("Оставьте пустым, чтобы не менять.");
                    System.out.print("Вопрос (" + card.question + "): ");
                    String q = sc.nextLine();
                    if (q.isEmpty()) q = null;
                    System.out.print("Ответ (" + card.answer + "): ");
                    String a = sc.nextLine();
                    if (a.isEmpty()) a = null;
                    System.out.print("Категория (" + card.category + "): ");
                    String cat = sc.nextLine();
                    if (cat.isEmpty()) cat = null;
                    if (forge.editCard(id, q, a, cat)) {
                        System.out.println("✅ Обновлено");
                    } else {
                        System.out.println("❌ Ошибка");
                    }
                    break;
                }
                case "6": {
                    System.out.print("ID для удаления: ");
                    int id = Integer.parseInt(sc.nextLine());
                    if (forge.deleteCard(id)) {
                        System.out.println("✅ Удалено");
                    } else {
                        System.out.println("❌ Не найдено");
                    }
                    break;
                }
                case "7": {
                    System.out.print("Имя файла (CSV): ");
                    String file = sc.nextLine();
                    if (file.isEmpty()) file = "flashcards.csv";
                    try {
                        forge.exportCSV(file);
                        System.out.println("Экспортировано в " + file);
                    } catch (IOException e) {
                        System.out.println("Ошибка: " + e.getMessage());
                    }
                    break;
                }
                case "8": {
                    System.out.print("Имя файла (CSV): ");
                    String file = sc.nextLine();
                    if (file.isEmpty()) { System.out.println("Укажите файл"); break; }
                    try {
                        forge.importCSV(file);
                        System.out.println("Импортировано из " + file);
                    } catch (IOException e) {
                        System.out.println("Ошибка: " + e.getMessage());
                    }
                    break;
                }
                default: System.out.println("Неверный выбор");
            }
        }
    }

    // ========== GUI ==========
    static class FlashForgeGUI extends JFrame {
        private Forge forge = new Forge();
        private JTable table;
        private DefaultTableModel model;
        private JTextField qField, aField, catField;
        private JLabel statusLabel;

        public FlashForgeGUI() {
            forge.load();
            setTitle("🧠 FlashForge - Флеш-карточки");
            setSize(750, 550);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout(5,5));
            initUI();
            refreshTable();
        }

        void initUI() {
            JPanel top = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5,5,5,5);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0; gbc.gridy = 0; top.add(new JLabel("Вопрос:"), gbc);
            gbc.gridx = 1; qField = new JTextField(20); top.add(qField, gbc);
            gbc.gridx = 2; top.add(new JLabel("Ответ:"), gbc);
            gbc.gridx = 3; aField = new JTextField(20); top.add(aField, gbc);
            gbc.gridx = 4; top.add(new JLabel("Категория:"), gbc);
            gbc.gridx = 5; catField = new JTextField(12); top.add(catField, gbc);
            gbc.gridx = 6; JButton addBtn = new JButton("➕ Добавить");
            addBtn.addActionListener(e -> addCard());
            top.add(addBtn, gbc);
            add(top, BorderLayout.NORTH);

            model = new DefaultTableModel(new String[]{"ID","Вопрос","Ответ","Категория","Коробка"}, 0);
            table = new JTable(model);
            add(new JScrollPane(table), BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout());
            JButton studyBtn = new JButton("🧠 Изучать");
            studyBtn.addActionListener(e -> study());
            bottom.add(studyBtn);
            JButton editBtn = new JButton("✏️ Редактировать");
            editBtn.addActionListener(e -> editCard());
            bottom.add(editBtn);
            JButton deleteBtn = new JButton("🗑 Удалить");
            deleteBtn.addActionListener(e -> deleteCard());
            bottom.add(deleteBtn);
            JButton statsBtn = new JButton("📊 Статистика");
            statsBtn.addActionListener(e -> showStats());
            bottom.add(statsBtn);
            JButton exportBtn = new JButton("💾 Экспорт CSV");
            exportBtn.addActionListener(e -> exportCSV());
            bottom.add(exportBtn);
            statusLabel = new JLabel(" ");
            bottom.add(statusLabel);
            add(bottom, BorderLayout.SOUTH);
        }

        void refreshTable() {
            model.setRowCount(0);
            for (FlashCard c : forge.cards) {
                model.addRow(new Object[]{c.id, c.question, c.answer, c.category, c.box});
            }
        }

        void addCard() {
            String q = qField.getText().trim();
            String a = aField.getText().trim();
            String cat = catField.getText().trim();
            if (cat.isEmpty()) cat = "General";
            if (q.isEmpty() || a.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Вопрос и ответ обязательны");
                return;
            }
            forge.addCard(q, a, cat);
            qField.setText("");
            aField.setText("");
            catField.setText("");
            refreshTable();
            statusLabel.setText("✅ Карточка добавлена");
        }

        int getSelectedId() {
            int row = table.getSelectedRow();
            if (row == -1) { JOptionPane.showMessageDialog(this, "Выберите карточку"); return -1; }
            return (int) model.getValueAt(row, 0);
        }

        void editCard() {
            int id = getSelectedId();
            if (id == -1) return;
            FlashCard card = forge.cards.stream().filter(c -> c.id == id).findFirst().orElse(null);
            if (card == null) return;
            JDialog dialog = new JDialog(this, "Редактировать", true);
            dialog.setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5,5,5,5);
            gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("Вопрос:"), gbc);
            gbc.gridx = 1; JTextField qEdit = new JTextField(card.question, 20); dialog.add(qEdit, gbc);
            gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("Ответ:"), gbc);
            gbc.gridx = 1; JTextField aEdit = new JTextField(card.answer, 20); dialog.add(aEdit, gbc);
            gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("Категория:"), gbc);
            gbc.gridx = 1; JTextField catEdit = new JTextField(card.category, 15); dialog.add(catEdit, gbc);
            gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2;
            JButton saveBtn = new JButton("Сохранить");
            saveBtn.addActionListener(e -> {
                String q = qEdit.getText().trim();
                String a = aEdit.getText().trim();
                String cat = catEdit.getText().trim();
                if (q.isEmpty() || a.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "Вопрос и ответ обязательны");
                    return;
                }
                if (forge.editCard(id, q, a, cat.isEmpty() ? null : cat)) {
                    refreshTable();
                    dialog.dispose();
                    statusLabel.setText("✅ Обновлено");
                }
            });
            dialog.add(saveBtn, gbc);
            dialog.pack();
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        }

        void deleteCard() {
            int id = getSelectedId();
            if (id != -1 && JOptionPane.showConfirmDialog(this, "Удалить карточку?") == JOptionPane.YES_OPTION) {
                if (forge.deleteCard(id)) {
                    refreshTable();
                    statusLabel.setText("✅ Удалено");
                }
            }
        }

        void study() {
            List<FlashCard> cards = forge.getCardsForReview(10);
            if (cards.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Нет карточек для повторения");
                return;
            }
            JDialog dialog = new JDialog(this, "🧠 Изучение", true);
            dialog.setSize(500, 400);
            dialog.setLayout(new BorderLayout());
            JPanel center = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(10,10,10,10);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            JLabel idxLabel = new JLabel();
            idxLabel.setFont(new Font("Arial", Font.BOLD, 12));
            gbc.gridx = 0; gbc.gridy = 0; center.add(idxLabel, gbc);
            JLabel catLabel = new JLabel();
            gbc.gridy = 1; center.add(catLabel, gbc);
            JLabel qLabel = new JLabel();
            qLabel.setFont(new Font("Arial", Font.PLAIN, 16));
            gbc.gridy = 2; center.add(qLabel, gbc);
            JLabel aLabel = new JLabel();
            aLabel.setFont(new Font("Arial", Font.PLAIN, 14));
            aLabel.setVisible(false);
            gbc.gridy = 3; center.add(aLabel, gbc);
            JButton showBtn = new JButton("Показать ответ");
            gbc.gridy = 4; center.add(showBtn, gbc);
            JButton correctBtn = new JButton("✅ Правильно");
            correctBtn.setEnabled(false);
            gbc.gridy = 5; center.add(correctBtn, gbc);
            JButton wrongBtn = new JButton("❌ Неправильно");
            wrongBtn.setEnabled(false);
            gbc.gridy = 6; center.add(wrongBtn, gbc);
            dialog.add(center, BorderLayout.CENTER);

            int[] idx = {0};
            FlashCard[] current = {null};

            void showCard() {
                if (idx[0] >= cards.size()) {
                    center.removeAll();
                    JLabel done = new JLabel("🎯 Изучение завершено!", SwingConstants.CENTER);
                    done.setFont(new Font("Arial", Font.BOLD, 18));
                    center.add(done);
                    dialog.revalidate();
                    dialog.repaint();
                    return;
                }
                current[0] = cards.get(idx[0]);
                idxLabel.setText(String.format("Карточка %d/%d", idx[0]+1, cards.size()));
                catLabel.setText("[" + current[0].category + "] Коробка " + current[0].box);
                qLabel.setText(current[0].question);
                aLabel.setText("Ответ: " + current[0].answer);
                aLabel.setVisible(false);
                showBtn.setEnabled(true);
                correctBtn.setEnabled(false);
                wrongBtn.setEnabled(false);
            }

            showBtn.addActionListener(e -> {
                aLabel.setVisible(true);
                showBtn.setEnabled(false);
                correctBtn.setEnabled(true);
                wrongBtn.setEnabled(true);
            });

            correctBtn.addActionListener(e -> {
                forge.updateCardAfterReview(current[0].id, true);
                idx[0]++;
                showCard();
            });

            wrongBtn.addActionListener(e -> {
                forge.updateCardAfterReview(current[0].id, false);
                idx[0]++;
                showCard();
            });

            showCard();
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        }

        void showStats() {
            Map<String, Object> stats = forge.getStatistics();
            StringBuilder sb = new StringBuilder();
            sb.append("📊 СТАТИСТИКА\n\n");
            sb.append("Всего карточек: ").append(stats.get("total")).append("\n");
            sb.append("Правильных ответов: ").append(stats.get("correct")).append("\n");
            sb.append("Неправильных ответов: ").append(stats.get("wrong")).append("\n");
            sb.append(String.format("Средняя правильность: %.2f%%\n", stats.get("avgCorrect")));
            sb.append("\nРаспределение по коробкам (Лейтнер):\n");
            Map<Integer, Integer> byBox = (Map<Integer, Integer>) stats.get("byBox");
            for (int i = 1; i <= 5; i++) {
                sb.append("  Коробка ").append(i).append(": ").append(byBox.get(i)).append(" карточек\n");
            }
            JOptionPane.showMessageDialog(this, sb.toString());
        }

        void exportCSV() {
            JFileChooser fc = new JFileChooser();
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    forge.exportCSV(fc.getSelectedFile().getAbsolutePath());
                    JOptionPane.showMessageDialog(this, "Экспортировано");
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Ошибка: " + e.getMessage());
                }
            }
        }
    }
}
