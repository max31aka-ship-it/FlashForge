#!/usr/bin/env node
/**
 * flash_forge.js - Флеш-карточки на JavaScript (Node.js CLI + веб)
 */
const fs = require('fs');
const path = require('path');
const { program } = require('commander');
const readline = require('readline');

const DATA_FILE = path.join(__dirname, 'flashcards.json');

class FlashCard {
    constructor(question, answer, category = 'General') {
        this.id = Date.now() + Math.random() * 1000;
        this.question = question;
        this.answer = answer;
        this.category = category;
        this.box = 1;
        this.nextReview = new Date().toISOString().slice(0,10);
        this.created = new Date().toISOString();
        this.correctCount = 0;
        this.wrongCount = 0;
    }
}

class FlashForge {
    constructor() {
        this.cards = [];
        this.load();
    }

    load() {
        if (fs.existsSync(DATA_FILE)) {
            try {
                this.cards = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
            } catch {}
        }
    }

    save() {
        fs.writeFileSync(DATA_FILE, JSON.stringify(this.cards, null, 2));
    }

    addCard(question, answer, category) {
        const card = new FlashCard(question, answer, category);
        this.cards.push(card);
        this.save();
        return card;
    }

    editCard(id, question, answer, category) {
        const card = this.cards.find(c => c.id === id);
        if (!card) return false;
        if (question) card.question = question;
        if (answer) card.answer = answer;
        if (category) card.category = category;
        this.save();
        return true;
    }

    deleteCard(id) {
        const idx = this.cards.findIndex(c => c.id === id);
        if (idx === -1) return false;
        this.cards.splice(idx, 1);
        this.save();
        return true;
    }

    getCardsForReview(count = 10) {
        const today = new Date().toISOString().slice(0,10);
        const ready = this.cards.filter(c => c.nextReview <= today);
        if (!ready.length) return [];
        ready.sort((a, b) => a.box - b.box);
        const shuffled = ready.sort(() => Math.random() - 0.5);
        return shuffled.slice(0, Math.min(count, shuffled.length));
    }

    updateCardAfterReview(id, correct) {
        const card = this.cards.find(c => c.id === id);
        if (!card) return;
        if (correct) {
            card.correctCount++;
            card.box = Math.min(card.box + 1, 5);
            const intervals = [1, 3, 7, 14, 30];
            const nextDate = new Date();
            nextDate.setDate(nextDate.getDate() + intervals[card.box - 1]);
            card.nextReview = nextDate.toISOString().slice(0,10);
        } else {
            card.wrongCount++;
            card.box = 1;
            card.nextReview = new Date().toISOString().slice(0,10);
        }
        this.save();
    }

    getStatistics() {
        const total = this.cards.length;
        if (!total) return { total: 0, correct: 0, wrong: 0, avgCorrect: 0, byBox: {} };
        const correct = this.cards.reduce((s, c) => s + c.correctCount, 0);
        const wrong = this.cards.reduce((s, c) => s + c.wrongCount, 0);
        const byBox = {};
        for (let i = 1; i <= 5; i++) {
            byBox[i] = this.cards.filter(c => c.box === i).length;
        }
        return { total, correct, wrong, avgCorrect: correct / total * 100, byBox };
    }

    getCategories() {
        return [...new Set(this.cards.map(c => c.category))].sort();
    }

    exportCSV(filepath) {
        const lines = ['ID,Question,Answer,Category,Box,NextReview,Created,Correct,Wrong'];
        this.cards.forEach(c => {
            lines.push(`${c.id},"${c.question}","${c.answer}","${c.category}",${c.box},${c.nextReview},${c.created},${c.correctCount},${c.wrongCount}`);
        });
        fs.writeFileSync(filepath, lines.join('\n'));
    }

    importCSV(filepath) {
        const content = fs.readFileSync(filepath, 'utf8');
        const lines = content.split('\n').filter(l => l.trim());
        const header = lines[0].split(',');
        for (let i = 1; i < lines.length; i++) {
            const parts = lines[i].match(/(".*?"|[^",\s]+)(?=\s*,|\s*$)/g);
            if (parts && parts.length >= 9) {
                const q = parts[1].replace(/^"|"$/g, '');
                const a = parts[2].replace(/^"|"$/g, '');
                const cat = parts[3].replace(/^"|"$/g, '') || 'General';
                this.addCard(q, a, cat);
            }
        }
    }
}

program
    .command('add <question> <answer>')
    .option('-c, --category <category>', 'Категория', 'General')
    .action((question, answer, options) => {
        const forge = new FlashForge();
        const card = forge.addCard(question, answer, options.category);
        console.log(`✅ Карточка ${card.id} добавлена`);
    });

program
    .command('list')
    .option('-c, --category <category>', 'Категория')
    .action((options) => {
        const forge = new FlashForge();
        const cards = options.category ? forge.cards.filter(c => c.category === options.category) : forge.cards;
        if (!cards.length) {
            console.log('Нет карточек.');
            return;
        }
        console.log('ID'.padEnd(20) + 'Вопрос'.padEnd(30) + 'Ответ'.padEnd(30) + 'Категория'.padEnd(15) + 'Коробка');
        cards.forEach(c => {
            console.log(`${c.id.toString().padEnd(20)} ${c.question.slice(0,28).padEnd(30)} ${c.answer.slice(0,28).padEnd(30)} ${c.category.padEnd(15)} ${c.box}`);
        });
    });

program
    .command('study')
    .option('--count <count>', 'Количество карточек', parseInt, 10)
    .action(async (options) => {
        const forge = new FlashForge();
        const cards = forge.getCardsForReview(options.count);
        if (!cards.length) {
            console.log('Нет карточек для повторения.');
            return;
        }
        console.log(`\n🧠 Начинаем изучение! ${cards.length} карточек.\n`);
        const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
        const prompt = (q) => new Promise(resolve => rl.question(q, resolve));

        for (let i = 0; i < cards.length; i++) {
            const card = cards[i];
            console.log(`[${i+1}/${cards.length}] Вопрос: ${card.question}`);
            await prompt('Нажмите Enter, чтобы увидеть ответ...');
            console.log(`Ответ: ${card.answer}`);
            let answered = false;
            while (!answered) {
                const ans = await prompt('Правильно? (y/n): ');
                if (ans.toLowerCase() === 'y' || ans.toLowerCase() === 'n') {
                    forge.updateCardAfterReview(card.id, ans.toLowerCase() === 'y');
                    answered = true;
                } else {
                    console.log('Введите y или n');
                }
            }
            console.log();
        }
        console.log('🎯 Изучение завершено!');
        rl.close();
    });

program
    .command('stats')
    .action(() => {
        const forge = new FlashForge();
        const stats = forge.getStatistics();
        console.log('📊 СТАТИСТИКА');
        console.log(`Всего карточек: ${stats.total}`);
        console.log(`Правильных ответов: ${stats.correct}`);
        console.log(`Неправильных ответов: ${stats.wrong}`);
        console.log(`Средняя правильность: ${stats.avgCorrect.toFixed(2)}%`);
        console.log('Распределение по коробкам (Лейтнер):');
        for (let i = 1; i <= 5; i++) {
            console.log(`  Коробка ${i}: ${stats.byBox[i] || 0} карточек`);
        }
    });

program
    .command('edit')
    .requiredOption('--id <id>', 'ID карточки', parseInt)
    .option('--question <question>', 'Новый вопрос')
    .option('--answer <answer>', 'Новый ответ')
    .option('--category <category>', 'Новая категория')
    .action((options) => {
        const forge = new FlashForge();
        if (forge.editCard(options.id, options.question, options.answer, options.category)) {
            console.log(`✅ Карточка ${options.id} обновлена`);
        } else {
            console.log(`❌ Карточка ${options.id} не найдена`);
        }
    });

program
    .command('delete')
    .requiredOption('--id <id>', 'ID карточки', parseInt)
    .action((options) => {
        const forge = new FlashForge();
        if (forge.deleteCard(options.id)) {
            console.log(`✅ Карточка ${options.id} удалена`);
        } else {
            console.log(`❌ Карточка ${options.id} не найдена`);
        }
    });

program
    .command('export')
    .requiredOption('-o, --output <file>', 'Имя CSV файла')
    .action((options) => {
        const forge = new FlashForge();
        forge.exportCSV(options.output);
        console.log(`Экспортировано в ${options.output}`);
    });

program
    .command('import')
    .requiredOption('-f, --file <file>', 'CSV файл')
    .action((options) => {
        const forge = new FlashForge();
        forge.importCSV(options.file);
        console.log(`Импортировано из ${options.file}`);
    });

if (process.argv.length <= 2) {
    // Interactive mode
    const forge = new FlashForge();
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    const prompt = (q) => new Promise(resolve => rl.question(q, resolve));

    (async () => {
        while (true) {
            console.log('\n🧠 FlashForge - Флеш-карточки (интерактивный)');
            console.log('1. Добавить карточку');
            console.log('2. Список карточек');
            console.log('3. Начать изучение');
            console.log('4. Статистика');
            console.log('5. Редактировать');
            console.log('6. Удалить');
            console.log('7. Экспорт CSV');
            console.log('8. Импорт CSV');
            console.log('0. Выход');
            const choice = await prompt('Выберите действие: ');
            switch (choice.trim()) {
                case '0': rl.close(); return;
                case '1': {
                    const q = await prompt('Вопрос: ');
                    if (!q) { console.log('Вопрос обязателен'); break; }
                    const a = await prompt('Ответ: ');
                    if (!a) { console.log('Ответ обязателен'); break; }
                    const cat = await prompt('Категория (по умолчанию General): ') || 'General';
                    const card = forge.addCard(q, a, cat);
                    console.log(`✅ Карточка ${card.id} добавлена`);
                    break;
                }
                case '2': {
                    const cat = await prompt('Категория (Enter все): ') || undefined;
                    const cards = cat ? forge.cards.filter(c => c.category === cat) : forge.cards;
                    if (!cards.length) { console.log('Нет карточек.'); break; }
                    console.log('ID'.padEnd(20) + 'Вопрос'.padEnd(30) + 'Ответ'.padEnd(30) + 'Категория'.padEnd(15) + 'Коробка');
                    cards.forEach(c => {
                        console.log(`${c.id.toString().padEnd(20)} ${c.question.slice(0,28).padEnd(30)} ${c.answer.slice(0,28).padEnd(30)} ${c.category.padEnd(15)} ${c.box}`);
                    });
                    break;
                }
                case '3': {
                    const count = parseInt(await prompt('Количество карточек (по умолчанию 10): ') || '10');
                    const cards = forge.getCardsForReview(count);
                    if (!cards.length) { console.log('Нет карточек для повторения.'); break; }
                    console.log(`\n🧠 Начинаем изучение! ${cards.length} карточек.\n`);
                    for (let i = 0; i < cards.length; i++) {
                        const card = cards[i];
                        console.log(`[${i+1}/${cards.length}] Вопрос: ${card.question}`);
                        await prompt('Нажмите Enter, чтобы увидеть ответ...');
                        console.log(`Ответ: ${card.answer}`);
                        let answered = false;
                        while (!answered) {
                            const ans = await prompt('Правильно? (y/n): ');
                            if (ans.toLowerCase() === 'y' || ans.toLowerCase() === 'n') {
                                forge.updateCardAfterReview(card.id, ans.toLowerCase() === 'y');
                                answered = true;
                            } else {
                                console.log('Введите y или n');
                            }
                        }
                        console.log();
                    }
                    console.log('🎯 Изучение завершено!');
                    break;
                }
                case '4': {
                    const stats = forge.getStatistics();
                    console.log('📊 СТАТИСТИКА');
                    console.log(`Всего карточек: ${stats.total}`);
                    console.log(`Правильных ответов: ${stats.correct}`);
                    console.log(`Неправильных ответов: ${stats.wrong}`);
                    console.log(`Средняя правильность: ${stats.avgCorrect.toFixed(2)}%`);
                    console.log('Распределение по коробкам (Лейтнер):');
                    for (let i = 1; i <= 5; i++) {
                        console.log(`  Коробка ${i}: ${stats.byBox[i] || 0} карточек`);
                    }
                    break;
                }
                case '5': {
                    const id = parseFloat(await prompt('ID карточки: '));
                    const card = forge.cards.find(c => c.id === id);
                    if (!card) { console.log('Карточка не найдена'); break; }
                    console.log('Оставьте пустым, чтобы не менять.');
                    const newQ = await prompt(`Вопрос (${card.question}): `);
                    const newA = await prompt(`Ответ (${card.answer}): `);
                    const newCat = await prompt(`Категория (${card.category}): `);
                    if (forge.editCard(id, newQ || undefined, newA || undefined, newCat || undefined)) {
                        console.log('✅ Обновлено');
                    } else {
                        console.log('❌ Ошибка');
                    }
                    break;
                }
                case '6': {
                    const id = parseFloat(await prompt('ID для удаления: '));
                    if (forge.deleteCard(id)) {
                        console.log('✅ Удалено');
                    } else {
                        console.log('❌ Не найдено');
                    }
                    break;
                }
                case '7': {
                    const file = await prompt('Имя файла (CSV): ') || 'flashcards.csv';
                    forge.exportCSV(file);
                    console.log(`Экспортировано в ${file}`);
                    break;
                }
                case '8': {
                    const file = await prompt('Имя файла (CSV): ');
                    if (!file) { console.log('Укажите файл'); break; }
                    forge.importCSV(file);
                    console.log(`Импортировано из ${file}`);
                    break;
                }
                default: console.log('Неверный выбор');
            }
        }
    })();
} else {
    program.parse(process.argv);
}
