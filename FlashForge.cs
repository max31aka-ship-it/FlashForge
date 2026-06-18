// FlashForge.cs - Флеш-карточки на C# (CLI + WinForms)
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Windows.Forms;

namespace FlashForge
{
    public class FlashCard
    {
        public int Id { get; set; }
        public string Question { get; set; }
        public string Answer { get; set; }
        public string Category { get; set; }
        public int Box { get; set; }
        public string NextReview { get; set; }
        public string Created { get; set; }
        public int CorrectCount { get; set; }
        public int WrongCount { get; set; }
    }

    public class Forge
    {
        public List<FlashCard> Cards { get; set; } = new List<FlashCard>();
        public int NextId { get; set; } = 1;
        private const string DataFile = "flashcards.json";

        public void Load()
        {
            if (File.Exists(DataFile))
            {
                try
                {
                    string json = File.ReadAllText(DataFile);
                    var data = JsonSerializer.Deserialize<Forge>(json);
                    if (data != null)
                    {
                        Cards = data.Cards;
                        NextId = data.NextId;
                        return;
                    }
                }
                catch { }
            }
            Cards = new List<FlashCard>();
            NextId = 1;
        }

        public void Save()
        {
            string json = JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(DataFile, json);
        }

        public FlashCard AddCard(string question, string answer, string category)
        {
            if (string.IsNullOrEmpty(category)) category = "General";
            var card = new FlashCard
            {
                Id = NextId++,
                Question = question,
                Answer = answer,
                Category = category,
                Box = 1,
                NextReview = DateTime.Now.ToString("yyyy-MM-dd"),
                Created = DateTime.Now.ToString("o"),
                CorrectCount = 0,
                WrongCount = 0
            };
            Cards.Add(card);
            Save();
            return card;
        }

        public bool EditCard(int id, string question, string answer, string category)
        {
            var card = Cards.FirstOrDefault(c => c.Id == id);
            if (card == null) return false;
            if (question != null) card.Question = question;
            if (answer != null) card.Answer = answer;
            if (category != null) card.Category = category;
            Save();
            return true;
        }

        public bool DeleteCard(int id)
        {
            int removed = Cards.RemoveAll(c => c.Id == id);
            if (removed > 0) { Save(); return true; }
            return false;
        }

        public List<FlashCard> GetCardsForReview(int count)
        {
            string today = DateTime.Now.ToString("yyyy-MM-dd");
            var ready = Cards.Where(c => string.Compare(c.NextReview, today) <= 0).ToList();
            if (!ready.Any()) return new List<FlashCard>();
            var rnd = new Random();
            return ready.OrderBy(x => rnd.Next()).Take(count).ToList();
        }

        public void UpdateCardAfterReview(int id, bool correct)
        {
            var card = Cards.FirstOrDefault(c => c.Id == id);
            if (card == null) return;
            if (correct)
            {
                card.CorrectCount++;
                card.Box = Math.Min(card.Box + 1, 5);
                int[] intervals = { 1, 3, 7, 14, 30 };
                card.NextReview = DateTime.Now.AddDays(intervals[card.Box - 1]).ToString("yyyy-MM-dd");
            }
            else
            {
                card.WrongCount++;
                card.Box = 1;
                card.NextReview = DateTime.Now.ToString("yyyy-MM-dd");
            }
            Save();
        }

        public (int total, int correct, int wrong, double avgCorrect, Dictionary<int, int> byBox) GetStatistics()
        {
            int total = Cards.Count;
            if (total == 0) return (0, 0, 0, 0, new Dictionary<int, int>());
            int correct = Cards.Sum(c => c.CorrectCount);
            int wrong = Cards.Sum(c => c.WrongCount);
            double avgCorrect = (double)correct / total * 100;
            var byBox = new Dictionary<int, int> { { 1, 0 }, { 2, 0 }, { 3, 0 }, { 4, 0 }, { 5, 0 } };
            foreach (var c in Cards) byBox[c.Box]++;
            return (total, correct, wrong, avgCorrect, byBox);
        }

        public List<string> GetCategories()
        {
            return Cards.Select(c => c.Category).Distinct().OrderBy(c => c).ToList();
        }

        public void ExportCSV(string filepath)
        {
            using (var sw = new StreamWriter(filepath))
            {
                sw.WriteLine("ID,Question,Answer,Category,Box,NextReview,Created,Correct,Wrong");
                foreach (var c in Cards)
                    sw.WriteLine($"{c.Id},\"{c.Question}\",\"{c.Answer}\",\"{c.Category}\",{c.Box},{c.NextReview},{c.Created},{c.CorrectCount},{c.WrongCount}");
            }
        }

        public void ImportCSV(string filepath)
        {
            var lines = File.ReadAllLines(filepath);
            for (int i = 1; i < lines.Length; i++)
            {
                var parts = lines[i].Split(',');
                if (parts.Length < 9) continue;
                string q = parts[1].Trim('"');
                string a = parts[2].Trim('"');
                string cat = parts[3].Trim('"');
                if (string.IsNullOrEmpty(cat)) cat = "General";
                AddCard(q, a, cat);
            }
        }
    }

    class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            if (args.Length > 0 && args[0] == "--gui")
            {
                Application.EnableVisualStyles();
                Application.Run(new FlashForgeGUI());
                return;
            }
            var forge = new Forge();
            forge.Load();
            if (args.Length == 0) { InteractiveMode(forge); return; }
            try
            {
                string cmd = args[0];
                switch (cmd)
                {
                    case "add":
                        string q = null, a = null, cat = "General";
                        for (int i = 1; i < args.Length; i++)
                        {
                            if (args[i] == "--question") q = args[++i];
                            else if (args[i] == "--answer") a = args[++i];
                            else if (args[i] == "--category") cat = args[++i];
                        }
                        if (q == null || a == null) { Console.WriteLine("Укажите --question и --answer"); return; }
                        forge.AddCard(q, a, cat);
                        Console.WriteLine("✅ Карточка добавлена");
                        break;
                    case "list":
                        string filterCat = null;
                        for (int i = 1; i < args.Length; i++) if (args[i] == "--category") filterCat = args[++i];
                        var cards = string.IsNullOrEmpty(filterCat) ? forge.Cards : forge.Cards.Where(c => c.Category == filterCat).ToList();
                        if (!cards.Any()) { Console.WriteLine("Нет карточек."); break; }
                        Console.WriteLine($"{"ID",-4} {"Вопрос",-30} {"Ответ",-30} {"Категория",-15} {"Коробка",-8}");
                        foreach (var c in cards)
                            Console.WriteLine($"{c.Id,-4} {c.Question,-30} {c.Answer,-30} {c.Category,-15} {c.Box,-8}");
                        break;
                    case "study":
                        int count = 10;
                        for (int i = 1; i < args.Length; i++) if (args[i] == "--count") count = int.Parse(args[++i]);
                        var studyCards = forge.GetCardsForReview(count);
                        if (!studyCards.Any()) { Console.WriteLine("Нет карточек для повторения."); break; }
                        Console.WriteLine($"\n🧠 Начинаем изучение! {studyCards.Count} карточек.\n");
                        for (int i = 0; i < studyCards.Count; i++)
                        {
                            var c = studyCards[i];
                            Console.WriteLine($"[{i+1}/{studyCards.Count}] Вопрос: {c.Question}");
                            Console.Write("Нажмите Enter, чтобы увидеть ответ...");
                            Console.ReadLine();
                            Console.WriteLine($"Ответ: {c.Answer}");
                            while (true)
                            {
                                Console.Write("Правильно? (y/n): ");
                                string ans = Console.ReadLine()?.ToLower();
                                if (ans == "y" || ans == "n")
                                {
                                    forge.UpdateCardAfterReview(c.Id, ans == "y");
                                    break;
                                }
                                Console.WriteLine("Введите y или n");
                            }
                            Console.WriteLine();
                        }
                        Console.WriteLine("🎯 Изучение завершено!");
                        break;
                    case "stats":
                        var stats = forge.GetStatistics();
                        Console.WriteLine("📊 СТАТИСТИКА");
                        Console.WriteLine($"Всего карточек: {stats.total}");
                        Console.WriteLine($"Правильных ответов: {stats.correct}");
                        Console.WriteLine($"Неправильных ответов: {stats.wrong}");
                        Console.WriteLine($"Средняя правильность: {stats.avgCorrect:F2}%");
                        Console.WriteLine("Распределение по коробкам (Лейтнер):");
                        for (int i = 1; i <= 5; i++)
                            Console.WriteLine($"  Коробка {i}: {stats.byBox[i]} карточек");
                        break;
                    case "edit":
                        int id = 0; string newQ = null, newA = null, newCat = null;
                        for (int i = 1; i < args.Length; i++)
                        {
                            if (args[i] == "--id") id = int.Parse(args[++i]);
                            else if (args[i] == "--question") newQ = args[++i];
                            else if (args[i] == "--answer") newA = args[++i];
                            else if (args[i] == "--category") newCat = args[++i];
                        }
                        if (id == 0) { Console.WriteLine("Укажите --id"); return; }
                        if (forge.EditCard(id, newQ, newA, newCat))
                            Console.WriteLine($"✅ Карточка #{id} обновлена");
                        else
                            Console.WriteLine($"❌ Карточка #{id} не найдена");
                        break;
                    case "delete":
                        int delId = 0;
                        for (int i = 1; i < args.Length; i++) if (args[i] == "--id") delId = int.Parse(args[++i]);
                        if (delId == 0) { Console.WriteLine("Укажите --id"); return; }
                        if (forge.DeleteCard(delId))
                            Console.WriteLine($"✅ Карточка #{delId} удалена");
                        else
                            Console.WriteLine($"❌ Карточка #{delId} не найдена");
                        break;
                    case "export":
                        string output = null;
                        for (int i = 1; i < args.Length; i++) if (args[i] == "--output") output = args[++i];
                        if (output == null) { Console.WriteLine("Укажите --output"); return; }
                        forge.ExportCSV(output);
                        Console.WriteLine($"Экспортировано в {output}");
                        break;
                    case "import":
                        string file = null;
                        for (int i = 1; i < args.Length; i++) if (args[i] == "--file") file = args[++i];
                        if (file == null) { Console.WriteLine("Укажите --file"); return; }
                        forge.ImportCSV(file);
                        Console.WriteLine($"Импортировано из {file}");
                        break;
                    default: InteractiveMode(forge); break;
                }
            }
            catch (Exception e) { Console.WriteLine($"Ошибка: {e.Message}"); }
        }

        static void InteractiveMode(Forge forge)
        {
            while (true)
            {
                Console.WriteLine("\n🧠 FlashForge - Флеш-карточки (интерактивный)");
                Console.WriteLine("1. Добавить карточку");
                Console.WriteLine("2. Список карточек");
                Console.WriteLine("3. Начать изучение");
                Console.WriteLine("4. Статистика");
                Console.WriteLine("5. Редактировать");
                Console.WriteLine("6. Удалить");
                Console.WriteLine("7. Экспорт CSV");
                Console.WriteLine("8. Импорт CSV");
                Console.WriteLine("0. Выход");
                Console.Write("Выберите действие: ");
                string choice = Console.ReadLine();
                switch (choice)
                {
                    case "0": return;
                    case "1":
                        Console.Write("Вопрос: ");
                        string q = Console.ReadLine();
                        if (string.IsNullOrEmpty(q)) { Console.WriteLine("Вопрос обязателен"); break; }
                        Console.Write("Ответ: ");
                        string a = Console.ReadLine();
                        if (string.IsNullOrEmpty(a)) { Console.WriteLine("Ответ обязателен"); break; }
                        Console.Write("Категория (по умолчанию General): ");
                        string cat = Console.ReadLine();
                        if (string.IsNullOrEmpty(cat)) cat = "General";
                        forge.AddCard(q, a, cat);
                        Console.WriteLine("✅ Карточка добавлена");
                        break;
                    case "2":
                        Console.Write("Категория (Enter все): ");
                        string filter = Console.ReadLine();
                        var cards = string.IsNullOrEmpty(filter) ? forge.Cards : forge.Cards.Where(c => c.Category == filter).ToList();
                        if (!cards.Any()) { Console.WriteLine("Нет карточек."); break; }
                        Console.WriteLine($"{"ID",-4} {"Вопрос",-30} {"Ответ",-30} {"Категория",-15} {"Коробка",-8}");
                        foreach (var c in cards)
                            Console.WriteLine($"{c.Id,-4} {c.Question,-30} {c.Answer,-30} {c.Category,-15} {c.Box,-8}");
                        break;
                    case "3":
                        Console.Write("Количество карточек (по умолчанию 10): ");
                        string cntStr = Console.ReadLine();
                        int count = string.IsNullOrEmpty(cntStr) ? 10 : int.Parse(cntStr);
                        var studyCards = forge.GetCardsForReview(count);
                        if (!studyCards.Any()) { Console.WriteLine("Нет карточек для повторения."); break; }
                        Console.WriteLine($"\n🧠 Начинаем изучение! {studyCards.Count} карточек.\n");
                        for (int i = 0; i < studyCards.Count; i++)
                        {
                            var c = studyCards[i];
                            Console.WriteLine($"[{i+1}/{studyCards.Count}] Вопрос: {c.Question}");
                            Console.Write("Нажмите Enter, чтобы увидеть ответ...");
                            Console.ReadLine();
                            Console.WriteLine($"Ответ: {c.Answer}");
                            while (true)
                            {
                                Console.Write("Правильно? (y/n): ");
                                string ans = Console.ReadLine()?.ToLower();
                                if (ans == "y" || ans == "n")
                                {
                                    forge.UpdateCardAfterReview(c.Id, ans == "y");
                                    break;
                                }
                                Console.WriteLine("Введите y или n");
                            }
                            Console.WriteLine();
                        }
                        Console.WriteLine("🎯 Изучение завершено!");
                        break;
                    case "4":
                        var stats = forge.GetStatistics();
                        Console.WriteLine("📊 СТАТИСТИКА");
                        Console.WriteLine($"Всего карточек: {stats.total}");
                        Console.WriteLine($"Правильных ответов: {stats.correct}");
                        Console.WriteLine($"Неправильных ответов: {stats.wrong}");
                        Console.WriteLine($"Средняя правильность: {stats.avgCorrect:F2}%");
                        Console.WriteLine("Распределение по коробкам (Лейтнер):");
                        for (int i = 1; i <= 5; i++)
                            Console.WriteLine($"  Коробка {i}: {stats.byBox[i]} карточек");
                        break;
                    case "5":
                        Console.Write("ID карточки: ");
                        if (!int.TryParse(Console.ReadLine(), out int editId)) { Console.WriteLine("Неверный ID"); break; }
                        var card = forge.Cards.FirstOrDefault(c => c.Id == editId);
                        if (card == null) { Console.WriteLine("Карточка не найдена"); break; }
                        Console.WriteLine("Оставьте пустым, чтобы не менять.");
                        Console.Write($"Вопрос ({card.Question}): ");
                        string newQ = Console.ReadLine();
                        if (string.IsNullOrEmpty(newQ)) newQ = null;
                        Console.Write($"Ответ ({card.Answer}): ");
                        string newA = Console.ReadLine();
                        if (string.IsNullOrEmpty(newA)) newA = null;
                        Console.Write($"Категория ({card.Category}): ");
                        string newCat = Console.ReadLine();
                        if (string.IsNullOrEmpty(newCat)) newCat = null;
                        if (forge.EditCard
