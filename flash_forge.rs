// flash_forge.rs - Флеш-карточки на Rust (CLI)
use serde::{Serialize, Deserialize};
use std::collections::HashSet;
use std::fs;
use std::io::{self, Write, BufRead};
use std::path::Path;
use std::str::FromStr;
use rand::seq::SliceRandom;
use rand::thread_rng;
use chrono::{Local, Duration};

#[derive(Serialize, Deserialize, Clone)]
struct FlashCard {
    id: u32,
    question: String,
    answer: String,
    category: String,
    box: u32,
    next_review: String,
    created: String,
    correct_count: u32,
    wrong_count: u32,
}

#[derive(Serialize, Deserialize)]
struct Forge {
    cards: Vec<FlashCard>,
    next_id: u32,
}

const DATA_FILE: &str = "flashcards.json";

impl Forge {
    fn load() -> Self {
        let mut f = Forge { cards: vec![], next_id: 1 };
        if Path::new(DATA_FILE).exists() {
            if let Ok(data) = fs::read_to_string(DATA_FILE) {
                if let Ok(forge) = serde_json::from_str(&data) {
                    return forge;
                }
            }
        }
        f
    }

    fn save(&self) {
        let data = serde_json::to_string_pretty(self).unwrap();
        fs::write(DATA_FILE, data).unwrap();
    }

    fn add_card(&mut self, question: &str, answer: &str, category: &str) -> FlashCard {
        let card = FlashCard {
            id: self.next_id,
            question: question.to_string(),
            answer: answer.to_string(),
            category: if category.is_empty() { "General".to_string() } else { category.to_string() },
            box: 1,
            next_review: Local::now().format("%Y-%m-%d").to_string(),
            created: Local::now().to_rfc3339(),
            correct_count: 0,
            wrong_count: 0,
        };
        self.cards.push(card.clone());
        self.next_id += 1;
        self.save();
        card
    }

    fn edit_card(&mut self, id: u32, question: Option<&str>, answer: Option<&str>, category: Option<&str>) -> bool {
        for card in &mut self.cards {
            if card.id == id {
                if let Some(q) = question { card.question = q.to_string(); }
                if let Some(a) = answer { card.answer = a.to_string(); }
                if let Some(c) = category { card.category = c.to_string(); }
                self.save();
                return true;
            }
        }
        false
    }

    fn delete_card(&mut self, id: u32) -> bool {
        let len = self.cards.len();
        self.cards.retain(|c| c.id != id);
        if self.cards.len() < len {
            self.save();
            return true;
        }
        false
    }

    fn get_cards_for_review(&self, count: usize) -> Vec<FlashCard> {
        let today = Local::now().format("%Y-%m-%d").to_string();
        let mut ready: Vec<_> = self.cards.iter().filter(|c| c.next_review <= today).cloned().collect();
        if ready.is_empty() {
            return vec![];
        }
        ready.sort_by(|a, b| a.box.cmp(&b.box));
        let mut rng = thread_rng();
        ready.shuffle(&mut rng);
        ready.truncate(count.min(ready.len()));
        ready
    }

    fn update_card_after_review(&mut self, id: u32, correct: bool) {
        for card in &mut self.cards {
            if card.id == id {
                if correct {
                    card.correct_count += 1;
                    card.box = (card.box + 1).min(5);
                    let intervals = [1, 3, 7, 14, 30];
                    let next_date = Local::now() + Duration::days(intervals[(card.box - 1) as usize]);
                    card.next_review = next_date.format("%Y-%m-%d").to_string();
                } else {
                    card.wrong_count += 1;
                    card.box = 1;
                    card.next_review = Local::now().format("%Y-%m-%d").to_string();
                }
                self.save();
                return;
            }
        }
    }

    fn get_statistics(&self) -> (u32, u32, u32, f64, Vec<(u32, u32)>) {
        let total = self.cards.len() as u32;
        if total == 0 {
            return (0, 0, 0, 0.0, vec![]);
        }
        let correct = self.cards.iter().map(|c| c.correct_count).sum();
        let wrong = self.cards.iter().map(|c| c.wrong_count).sum();
        let avg_correct = if total > 0 { (correct as f64 / total as f64) * 100.0 } else { 0.0 };
        let mut by_box = vec![(1, 0), (2, 0), (3, 0), (4, 0), (5, 0)];
        for c in &self.cards {
            for (box_num, count) in &mut by_box {
                if *box_num == c.box {
                    *count += 1;
                    break;
                }
            }
        }
        (total, correct, wrong, avg_correct, by_box)
    }

    fn get_categories(&self) -> Vec<String> {
        let mut set = HashSet::new();
        for c in &self.cards {
            set.insert(c.category.clone());
        }
        let mut cats: Vec<_> = set.into_iter().collect();
        cats.sort();
        cats
    }

    fn export_csv(&self, filepath: &str) -> Result<(), Box<dyn std::error::Error>> {
        let mut writer = csv::Writer::from_path(filepath)?;
        writer.write_record(&["ID", "Question", "Answer", "Category", "Box", "NextReview", "Created", "Correct", "Wrong"])?;
        for c in &self.cards {
            writer.serialize((c.id, &c.question, &c.answer, &c.category, c.box, &c.next_review, &c.created, c.correct_count, c.wrong_count))?;
        }
        writer.flush()?;
        Ok(())
    }

    fn import_csv(&mut self, filepath: &str) -> Result<(), Box<dyn std::error::Error>> {
        let mut reader = csv::Reader::from_path(filepath)?;
        for result in reader.records() {
            let record = result?;
            if record.len() >= 9 {
                self.add_card(&record[1], &record[2], &record[3]);
            }
        }
        Ok(())
    }
}

fn truncate(s: &str, max: usize) -> String {
    if s.len() > max {
        format!("{}...", &s[..max])
    } else {
        s.to_string()
    }
}

fn read_line(prompt: &str) -> String {
    print!("{}", prompt);
    io::stdout().flush().unwrap();
    let mut input = String::new();
    io::stdin().read_line(&mut input).unwrap();
    input.trim().to_string()
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 2 {
        interactive_mode();
        return;
    }
    let mut forge = Forge::load();
    match args[1].as_str() {
        "add" => {
            let mut question = String::new();
            let mut answer = String::new();
            let mut category = "General".to_string();
            let mut i = 2;
            while i < args.len() {
                match args[i].as_str() {
                    "--question" => { question = args[i+1].clone(); i += 2; }
                    "--answer" => { answer = args[i+1].clone(); i += 2; }
                    "--category" => { category = args[i+1].clone(); i += 2; }
                    _ => { i += 1; }
                }
            }
            if question.is_empty() || answer.is_empty() {
                println!("Укажите --question и --answer");
                return;
            }
            let card = forge.add_card(&question, &answer, &category);
            println!("✅ Карточка #{} добавлена", card.id);
        }
        "list" => {
            let mut category = None;
            let mut i = 2;
            while i < args.len() {
                if args[i] == "--category" {
                    category = Some(args[i+1].clone());
                    i += 2;
                } else { i += 1; }
            }
            let cards = if let Some(cat) = category {
                forge.cards.iter().filter(|c| c.category == cat).cloned().collect::<Vec<_>>()
            } else {
                forge.cards.clone()
            };
            if cards.is_empty() {
                println!("Нет карточек.");
            } else {
                println!("{:<4} {:<30} {:<30} {:<15} {:<8}", "ID", "Вопрос", "Ответ", "Категория", "Коробка");
                for c in cards {
                    println!("{:<4} {:<30} {:<30} {:<15} {:<8}", c.id, truncate(&c.question, 28), truncate(&c.answer, 28), c.category, c.box);
                }
            }
        }
        "study" => {
            let mut count = 10;
            let mut i = 2;
            while i < args.len() {
                if args[i] == "--count" {
                    count = args[i+1].parse().unwrap_or(10);
                    i += 2;
                } else { i += 1; }
            }
            let cards = forge.get_cards_for_review(count);
            if cards.is_empty() {
                println!("Нет карточек для повторения.");
                return;
            }
            println!("\n🧠 Начинаем изучение! {} карточек.\n", cards.len());
            for (i, card) in cards.iter().enumerate() {
                println!("[{}/{}] Вопрос: {}", i+1, cards.len(), card.question);
                read_line("Нажмите Enter, чтобы увидеть ответ...");
                println!("Ответ: {}", card.answer);
                loop {
                    let ans = read_line("Правильно? (y/n): ");
                    if ans == "y" || ans == "n" {
                        forge.update_card_after_review(card.id, ans == "y");
                        break;
                    }
                    println!("Введите y или n");
                }
                println!();
            }
            println!("🎯 Изучение завершено!");
        }
        "stats" => {
            let (total, correct, wrong, avg, by_box) = forge.get_statistics();
            println!("📊 СТАТИСТИКА");
            println!("Всего карточек: {}", total);
            println!("Правильных ответов: {}", correct);
            println!("Неправильных ответов: {}", wrong);
            println!("Средняя правильность: {:.2}%", avg);
            println!("Распределение по коробкам (Лейтнер):");
            for (box_num, count) in by_box {
                println!("  Коробка {}: {} карточек", box_num, count);
            }
        }
        "edit" => {
            let mut id = 0;
            let mut question = None;
            let mut answer = None;
            let mut category = None;
            let mut i = 2;
            while i < args.len() {
                match args[i].as_str() {
                    "--id" => { id = args[i+1].parse().unwrap_or(0); i += 2; }
                    "--question" => { question = Some(args[i+1].clone()); i += 2; }
                    "--answer" => { answer = Some(args[i+1].clone()); i += 2; }
                    "--category" => { category = Some(args[i+1].clone()); i += 2; }
                    _ => { i += 1; }
                }
            }
            if id == 0 {
                println!("Укажите --id");
                return;
            }
            if forge.edit_card(id, question.as_deref(), answer.as_deref(), category.as_deref()) {
                println!("✅ Карточка #{} обновлена", id);
            } else {
                println!("❌ Карточка #{} не найдена", id);
            }
        }
        "delete" => {
            let mut id = 0;
            let mut i = 2;
            while i < args.len() {
                if args[i] == "--id" {
                    id = args[i+1].parse().unwrap_or(0);
                    i += 2;
                } else { i += 1; }
            }
            if id == 0 {
                println!("Укажите --id");
                return;
            }
            if forge.delete_card(id) {
                println!("✅ Карточка #{} удалена", id);
            } else {
                println!("❌ Карточка #{} не найдена", id);
            }
        }
        "export" => {
            let mut output = String::new();
            let mut i = 2;
            while i < args.len() {
                if args[i] == "--output" {
                    output = args[i+1].clone();
                    i += 2;
                } else { i += 1; }
            }
            if output.is_empty() {
                println!("Укажите --output");
                return;
            }
            if let Err(e) = forge.export_csv(&output) {
                println!("Ошибка экспорта: {}", e);
            } else {
                println!("Экспортировано в {}", output);
            }
        }
        "import" => {
            let mut file = String::new();
            let mut i = 2;
            while i < args.len() {
                if args[i] == "--file" {
                    file = args[i+1].clone();
                    i += 2;
                } else { i += 1; }
            }
            if file.is_empty() {
                println!("Укажите --file");
                return;
            }
            if let Err(e) = forge.import_csv(&file) {
                println!("Ошибка импорта: {}", e);
            } else {
                println!("Импортировано из {}", file);
            }
        }
        _ => interactive_mode(),
    }
}

fn interactive_mode() {
    let mut forge = Forge::load();
    let stdin = io::stdin();
    let mut stdout = io::stdout();
    loop {
        println!("\n🧠 FlashForge - Флеш-карточки (интерактивный)");
        println!("1. Добавить карточку");
        println!("2. Список карточек");
        println!("3. Начать изучение");
        println!("4. Статистика");
        println!("5. Редактировать");
        println!("6. Удалить");
        println!("7. Экспорт CSV");
        println!("8. Импорт CSV");
        println!("0. Выход");
        print!("Выберите действие: ");
        stdout.flush().unwrap();
        let mut choice = String::new();
        stdin.read_line(&mut choice).unwrap();
        match choice.trim() {
            "0" => break,
            "1" => {
                let q = read_line("Вопрос: ");
                if q.is_empty() { println!("Вопрос обязателен"); continue; }
                let a = read_line("Ответ: ");
                if a.is_empty() { println!("Ответ обязателен"); continue; }
                let cat = read_line("Категория (по умолчанию General): ");
                let cat = if cat.is_empty() { "General" } else { &cat };
                let card = forge.add_card(&q, &a, cat);
                println!("✅ Карточка #{} добавлена", card.id);
            }
            "2" => {
                let cat = read_line("Категория (Enter все): ");
                let cards = if cat.is_empty() {
                    forge.cards.clone()
                } else {
                    forge.cards.iter().filter(|c| c.category == cat).cloned().collect::<Vec<_>>()
                };
                if cards.is_empty() {
                    println!("Нет карточек.");
                } else {
                    println!("{:<4} {:<30} {:<30} {:<15} {:<8}", "ID", "Вопрос", "Ответ", "Категория", "Коробка");
                    for c in cards {
                        println!("{:<4} {:<30} {:<30} {:<15} {:<8}", c.id, truncate(&c.question, 28), truncate(&c.answer, 28), c.category, c.box);
                    }
                }
            }
            "3" => {
                let count_str = read_line("Количество карточек (по умолчанию 10): ");
                let count = if count_str.is_empty() { 10 } else { count_str.parse::<usize>().unwrap_or(10) };
                let cards = forge.get_cards_for_review(count);
                if cards.is_empty() {
                    println!("Нет карточек для повторения.");
                    continue;
                }
                println!("\n🧠 Начинаем изучение! {} карточек.\n", cards.len());
                for (i, card) in cards.iter().enumerate() {
                    println!("[{}/{}] Вопрос: {}", i+1, cards.len(), card.question);
                    read_line("Нажмите Enter, чтобы увидеть ответ...");
                    println!("Ответ: {}", card.answer);
                    loop {
                        let ans = read_line("Правильно? (y/n): ");
                        if ans == "y" || ans == "n" {
                            forge.update_card_after_review(card.id, ans == "y");
                            break;
                        }
                        println!("Введите y или n");
                    }
                    println!();
                }
                println!("🎯 Изучение завершено!");
            }
            "4" => {
                let (total, correct, wrong, avg, by_box) = forge.get_statistics();
                println!("📊 СТАТИСТИКА");
                println!("Всего карточек: {}", total);
                println!("Правильных ответов: {}", correct);
                println!("Неправильных ответов: {}", wrong);
                println!("Средняя правильность: {:.2}%", avg);
                println!("Распределение по коробкам (Лейтнер):");
                for (box_num, count) in by_box {
                    println!("  Коробка {}: {} карточек", box_num, count);
                }
            }
            "5" => {
                let id_str = read_line("ID карточки: ");
                let id = id_str.parse::<u32>().unwrap_or(0);
                if id == 0 { println!("Неверный ID"); continue; }
                let card = forge.cards.iter().find(|c| c.id == id).cloned();
                if card.is_none() { println!("Карточка не найдена"); continue; }
                let card = card.unwrap();
                println!("Оставьте пустым, чтобы не менять.");
                let new_q = read_line(&format!("Вопрос ({}): ", card.question));
                let new_a = read_line(&format!("Ответ ({}): ", card.answer));
                let new_cat = read_line(&format!("Категория ({}): ", card.category));
                let q_opt = if new_q.is_empty() { None } else { Some(new_q.as_str()) };
                let a_opt = if new_a.is_empty() { None } else { Some(new_a.as_str()) };
                let c_opt = if new_cat.is_empty() { None } else { Some(new_cat.as_str()) };
                if forge.edit_card(id, q_opt, a_opt, c_opt) {
                    println!("✅ Обновлено");
                } else {
                    println!("❌ Ошибка");
                }
            }
            "6" => {
                let id_str = read_line("ID для удаления: ");
                let id = id_str.parse::<u32>().unwrap_or(0);
                if id == 0 { println!("Неверный ID"); continue; }
                if forge.delete_card(id) {
                    println!("✅ Удалено");
                } else {
                    println!("❌ Не найдено");
                }
            }
            "7" => {
                let file = read_line("Имя файла (CSV): ");
                let file = if file.is_empty() { "flashcards.csv".to_string() } else { file };
                if let Err(e) = forge.export_csv(&file) {
                    println!("Ошибка экспорта: {}", e);
                } else {
                    println!("Экспортировано в {}", file);
                }
            }
            "8" => {
                let file = read_line("Имя файла (CSV): ");
                if file.is_empty() { println!("Укажите файл"); continue; }
                if let Err(e) = forge.import_csv(&file) {
                    println!("Ошибка импорта: {}", e);
                } else {
                    println!("Импортировано из {}", file);
                }
            }
            _ => println!("Неверный выбор"),
        }
    }
}
