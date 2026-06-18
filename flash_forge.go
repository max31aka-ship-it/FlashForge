// flash_forge.go - Флеш-карточки на Go (CLI)
package main

import (
	"bufio"
	"encoding/csv"
	"encoding/json"
	"flag"
	"fmt"
	"math/rand"
	"os"
	"strconv"
	"strings"
	"time"
)

type FlashCard struct {
	ID           int     `json:"id"`
	Question     string  `json:"question"`
	Answer       string  `json:"answer"`
	Category     string  `json:"category"`
	Box          int     `json:"box"`
	NextReview   string  `json:"next_review"`
	Created      string  `json:"created"`
	CorrectCount int     `json:"correct_count"`
	WrongCount   int     `json:"wrong_count"`
}

type Forge struct {
	Cards  []FlashCard `json:"cards"`
	NextID int         `json:"next_id"`
}

const dataFile = "flashcards.json"

func loadForge() *Forge {
	var f Forge
	file, err := os.ReadFile(dataFile)
	if err != nil {
		f.Cards = []FlashCard{}
		f.NextID = 1
		return &f
	}
	err = json.Unmarshal(file, &f)
	if err != nil {
		f.Cards = []FlashCard{}
		f.NextID = 1
	}
	return &f
}

func saveForge(f *Forge) {
	data, _ := json.MarshalIndent(f, "", "  ")
	os.WriteFile(dataFile, data, 0644)
}

func addCard(f *Forge, question, answer, category string) FlashCard {
	if category == "" {
		category = "General"
	}
	card := FlashCard{
		ID:           f.NextID,
		Question:     question,
		Answer:       answer,
		Category:     category,
		Box:          1,
		NextReview:   time.Now().Format("2006-01-02"),
		Created:      time.Now().Format(time.RFC3339),
		CorrectCount: 0,
		WrongCount:   0,
	}
	f.Cards = append(f.Cards, card)
	f.NextID++
	saveForge(f)
	return card
}

func editCard(f *Forge, id int, question, answer, category *string) bool {
	for i, c := range f.Cards {
		if c.ID == id {
			if question != nil {
				f.Cards[i].Question = *question
			}
			if answer != nil {
				f.Cards[i].Answer = *answer
			}
			if category != nil {
				f.Cards[i].Category = *category
			}
			saveForge(f)
			return true
		}
	}
	return false
}

func deleteCard(f *Forge, id int) bool {
	for i, c := range f.Cards {
		if c.ID == id {
			f.Cards = append(f.Cards[:i], f.Cards[i+1:]...)
			saveForge(f)
			return true
		}
	}
	return false
}

func getCardsForReview(f *Forge, count int) []FlashCard {
	today := time.Now().Format("2006-01-02")
	var ready []FlashCard
	for _, c := range f.Cards {
		if c.NextReview <= today {
			ready = append(ready, c)
		}
	}
	if len(ready) == 0 {
		return []FlashCard{}
	}
	// Sort by box
	for i := 0; i < len(ready); i++ {
		for j := i + 1; j < len(ready); j++ {
			if ready[i].Box > ready[j].Box {
				ready[i], ready[j] = ready[j], ready[i]
			}
		}
	}
	// Shuffle
	rand.Seed(time.Now().UnixNano())
	rand.Shuffle(len(ready), func(i, j int) { ready[i], ready[j] = ready[j], ready[i] })
	if count < len(ready) {
		return ready[:count]
	}
	return ready
}

func updateCardAfterReview(f *Forge, id int, correct bool) {
	for i, c := range f.Cards {
		if c.ID == id {
			if correct {
				f.Cards[i].CorrectCount++
				f.Cards[i].Box++
				if f.Cards[i].Box > 5 {
					f.Cards[i].Box = 5
				}
				intervals := []int{1, 3, 7, 14, 30}
				nextDate := time.Now().AddDate(0, 0, intervals[f.Cards[i].Box-1])
				f.Cards[i].NextReview = nextDate.Format("2006-01-02")
			} else {
				f.Cards[i].WrongCount++
				f.Cards[i].Box = 1
				f.Cards[i].NextReview = time.Now().Format("2006-01-02")
			}
			saveForge(f)
			return
		}
	}
}

func getStatistics(f *Forge) (total, correct, wrong int, avgCorrect float64, byBox map[int]int) {
	total = len(f.Cards)
	if total == 0 {
		return 0, 0, 0, 0, map[int]int{1: 0, 2: 0, 3: 0, 4: 0, 5: 0}
	}
	correct = 0
	wrong = 0
	byBox = map[int]int{1: 0, 2: 0, 3: 0, 4: 0, 5: 0}
	for _, c := range f.Cards {
		correct += c.CorrectCount
		wrong += c.WrongCount
		byBox[c.Box]++
	}
	if total > 0 {
		avgCorrect = float64(correct) / float64(total) * 100
	}
	return
}

func getCategories(f *Forge) []string {
	catMap := make(map[string]bool)
	for _, c := range f.Cards {
		catMap[c.Category] = true
	}
	var cats []string
	for c := range catMap {
		cats = append(cats, c)
	}
	sortStrings(cats)
	return cats
}

func sortStrings(s []string) {
	for i := 0; i < len(s); i++ {
		for j := i + 1; j < len(s); j++ {
			if s[i] > s[j] {
				s[i], s[j] = s[j], s[i]
			}
		}
	}
}

func exportCSV(f *Forge, filepath string) {
	file, err := os.Create(filepath)
	if err != nil {
		fmt.Println("Ошибка создания файла:", err)
		return
	}
	defer file.Close()
	writer := csv.NewWriter(file)
	defer writer.Flush()
	writer.Write([]string{"ID", "Question", "Answer", "Category", "Box", "NextReview", "Created", "Correct", "Wrong"})
	for _, c := range f.Cards {
		writer.Write([]string{
			strconv.Itoa(c.ID),
			c.Question,
			c.Answer,
			c.Category,
			strconv.Itoa(c.Box),
			c.NextReview,
			c.Created,
			strconv.Itoa(c.CorrectCount),
			strconv.Itoa(c.WrongCount),
		})
	}
}

func importCSV(f *Forge, filepath string) {
	file, err := os.Open(filepath)
	if err != nil {
		fmt.Println("Ошибка открытия файла:", err)
		return
	}
	defer file.Close()
	reader := csv.NewReader(file)
	records, _ := reader.ReadAll()
	if len(records) < 2 {
		return
	}
	for i := 1; i < len(records); i++ {
		row := records[i]
		if len(row) < 9 {
			continue
		}
		addCard(f, row[1], row[2], row[3])
	}
}

func main() {
	var (
		cmd      string
		question string
		answer   string
		category string
		id       int
		count    int
		output   string
		file     string
		newQ     string
		newA     string
		newCat   string
	)
	flag.StringVar(&cmd, "cmd", "", "Команда: add, list, study, stats, edit, delete, export, import")
	flag.StringVar(&question, "question", "", "Вопрос")
	flag.StringVar(&answer, "answer", "", "Ответ")
	flag.StringVar(&category, "category", "General", "Категория")
	flag.IntVar(&id, "id", 0, "ID карточки")
	flag.IntVar(&count, "count", 10, "Количество карточек")
	flag.StringVar(&output, "output", "", "Имя CSV файла")
	flag.StringVar(&file, "file", "", "CSV файл для импорта")
	flag.StringVar(&newQ, "new-question", "", "Новый вопрос")
	flag.StringVar(&newA, "new-answer", "", "Новый ответ")
	flag.StringVar(&newCat, "new-category", "", "Новая категория")
	flag.Parse()

	forge := loadForge()

	switch cmd {
	case "add":
		if question == "" || answer == "" {
			fmt.Println("Укажите --question и --answer")
			return
		}
		card := addCard(forge, question, answer, category)
		fmt.Printf("✅ Карточка #%d добавлена\n", card.ID)
	case "list":
		cards := forge.Cards
		if category != "" {
			var filtered []FlashCard
			for _, c := range cards {
				if c.Category == category {
					filtered = append(filtered, c)
				}
			}
			cards = filtered
		}
		if len(cards) == 0 {
			fmt.Println("Нет карточек.")
		} else {
			fmt.Printf("%-4s %-30s %-30s %-15s %-8s\n", "ID", "Вопрос", "Ответ", "Категория", "Коробка")
			for _, c := range cards {
				fmt.Printf("%-4d %-30s %-30s %-15s %-8d\n", c.ID, truncate(c.Question, 28), truncate(c.Answer, 28), c.Category, c.Box)
			}
		}
	case "study":
		cards := getCardsForReview(forge, count)
		if len(cards) == 0 {
			fmt.Println("Нет карточек для повторения.")
			return
		}
		fmt.Printf("\n🧠 Начинаем изучение! %d карточек.\n\n", len(cards))
		scanner := bufio.NewScanner(os.Stdin)
		for i, card := range cards {
			fmt.Printf("[%d/%d] Вопрос: %s\n", i+1, len(cards), card.Question)
			fmt.Print("Нажмите Enter, чтобы увидеть ответ...")
			scanner.Scan()
			fmt.Printf("Ответ: %s\n", card.Answer)
			for {
				fmt.Print("Правильно? (y/n): ")
				scanner.Scan()
				ans := scanner.Text()
				if ans == "y" || ans == "n" {
					updateCardAfterReview(forge, card.ID, ans == "y")
					break
				}
				fmt.Println("Введите y или n")
			}
			fmt.Println()
		}
		fmt.Println("🎯 Изучение завершено!")
	case "stats":
		total, correct, wrong, avgCorrect, byBox := getStatistics(forge)
		fmt.Println("📊 СТАТИСТИКА")
		fmt.Printf("Всего карточек: %d\n", total)
		fmt.Printf("Правильных ответов: %d\n", correct)
		fmt.Printf("Неправильных ответов: %d\n", wrong)
		fmt.Printf("Средняя правильность: %.2f%%\n", avgCorrect)
		fmt.Println("Распределение по коробкам (Лейтнер):")
		for i := 1; i <= 5; i++ {
			fmt.Printf("  Коробка %d: %d карточек\n", i, byBox[i])
		}
	case "edit":
		if id == 0 {
			fmt.Println("Укажите --id")
			return
		}
		var qPtr, aPtr, cPtr *string
		if newQ != "" {
			qPtr = &newQ
		}
		if newA != "" {
			aPtr = &newA
		}
		if newCat != "" {
			cPtr = &newCat
		}
		if editCard(forge, id, qPtr, aPtr, cPtr) {
			fmt.Printf("✅ Карточка #%d обновлена\n", id)
		} else {
			fmt.Printf("❌ Карточка #%d не найдена\n", id)
		}
	case "delete":
		if id == 0 {
			fmt.Println("Укажите --id")
			return
		}
		if deleteCard(forge, id) {
			fmt.Printf("✅ Карточка #%d удалена\n", id)
		} else {
			fmt.Printf("❌ Карточка #%d не найдена\n", id)
		}
	case "export":
		if output == "" {
			fmt.Println("Укажите --output")
			return
		}
		exportCSV(forge, output)
		fmt.Printf("Экспортировано в %s\n", output)
	case "import":
		if file == "" {
			fmt.Println("Укажите --file")
			return
		}
		importCSV(forge, file)
		fmt.Printf("Импортировано из %s\n", file)
	default:
		interactiveMode(forge)
	}
}

func truncate(s string, max int) string {
	if len(s) > max {
		return s[:max] + "..."
	}
	return s
}

func interactiveMode(f *Forge) {
	scanner := bufio.NewScanner(os.Stdin)
	for {
		fmt.Println("\n🧠 FlashForge - Флеш-карточки (интерактивный)")
		fmt.Println("1. Добавить карточку")
		fmt.Println("2. Список карточек")
		fmt.Println("3. Начать изучение")
		fmt.Println("4. Статистика")
		fmt.Println("5. Редактировать")
		fmt.Println("6. Удалить")
		fmt.Println("7. Экспорт CSV")
		fmt.Println("8. Импорт CSV")
		fmt.Println("0. Выход")
		fmt.Print("Выберите действие: ")
		scanner.Scan()
		choice := scanner.Text()
		switch choice {
		case "0":
			return
		case "1":
			fmt.Print("Вопрос: ")
			scanner.Scan()
			q := scanner.Text()
			if q == "" {
				fmt.Println("Вопрос обязателен")
				continue
			}
			fmt.Print("Ответ: ")
			scanner.Scan()
			a := scanner.Text()
			if a == "" {
				fmt.Println("Ответ обязателен")
				continue
			}
			fmt.Print("Категория (по умолчанию General): ")
			scanner.Scan()
			cat := scanner.Text()
			if cat == "" {
				cat = "General"
			}
			card := addCard(f, q, a, cat)
			fmt.Printf("✅ Карточка #%d добавлена\n", card.ID)
		case "2":
			fmt.Print("Категория (Enter все): ")
			scanner.Scan()
			cat := scanner.Text()
			cards := f.Cards
			if cat != "" {
				var filtered []FlashCard
				for _, c := range cards {
					if c.Category == cat {
						filtered = append(filtered, c)
					}
				}
				cards = filtered
			}
			if len(cards) == 0 {
				fmt.Println("Нет карточек.")
			} else {
				fmt.Printf("%-4s %-30s %-30s %-15s %-8s\n", "ID", "Вопрос", "Ответ", "Категория", "Коробка")
				for _, c := range cards {
					fmt.Printf("%-4d %-30s %-30s %-15s %-8d\n", c.ID, truncate(c.Question, 28), truncate(c.Answer, 28), c.Category, c.Box)
				}
			}
		case "3":
			fmt.Print("Количество карточек (по умолчанию 10): ")
			scanner.Scan()
			cntStr := scanner.Text()
			cnt := 10
			if cntStr != "" {
				cnt, _ = strconv.Atoi(cntStr)
			}
			cards := getCardsForReview(f, cnt)
			if len(cards) == 0 {
				fmt.Println("Нет карточек для повторения.")
				continue
			}
			fmt.Printf("\n🧠 Начинаем изучение! %d карточек.\n\n", len(cards))
			for i, card := range cards {
				fmt.Printf("[%d/%d] Вопрос: %s\n", i+1, len(cards), card.Question)
				fmt.Print("Нажмите Enter, чтобы увидеть ответ...")
				scanner.Scan()
				fmt.Printf("Ответ: %s\n", card.Answer)
				for {
					fmt.Print("Правильно? (y/n): ")
					scanner.Scan()
					ans := scanner.Text()
					if ans == "y" || ans == "n" {
						updateCardAfterReview(f, card.ID, ans == "y")
						break
					}
					fmt.Println("Введите y или n")
				}
				fmt.Println()
			}
			fmt.Println("🎯 Изучение завершено!")
		case "4":
			total, correct, wrong, avgCorrect, byBox := getStatistics(f)
			fmt.Println("📊 СТАТИСТИКА")
			fmt.Printf("Всего карточек: %d\n", total)
			fmt.Printf("Правильных ответов: %d\n", correct)
			fmt.Printf("Неправильных ответов: %d\n", wrong)
			fmt.Printf("Средняя правильность: %.2f%%\n", avgCorrect)
			fmt.Println("Распределение по коробкам (Лейтнер):")
			for i := 1; i <= 5; i++ {
				fmt.Printf("  Коробка %d: %d карточек\n", i, byBox[i])
			}
		case "5":
			fmt.Print("ID карточки: ")
			scanner.Scan()
			idStr := scanner.Text()
			id, _ := strconv.Atoi(idStr)
			var card *FlashCard
			for i, c := range f.Cards {
				if c.ID == id {
					card = &f.Cards[i]
					break
				}
			}
			if card == nil {
				fmt.Println("Карточка не найдена")
				continue
			}
			fmt.Println("Оставьте пустым, чтобы не менять.")
			fmt.Printf("Вопрос (%s): ", card.Question)
			scanner.Scan()
			newQ := scanner.Text()
			fmt.Printf("Ответ (%s): ", card.Answer)
			scanner.Scan()
			newA := scanner.Text()
			fmt.Printf("Категория (%s): ", card.Category)
			scanner.Scan()
			newCat := scanner.Text()
			var qPtr, aPtr, cPtr *string
			if newQ != "" {
				qPtr = &newQ
			}
			if newA != "" {
				aPtr = &newA
			}
			if newCat != "" {
				cPtr = &newCat
			}
			if editCard(f, id, qPtr, aPtr, cPtr) {
				fmt.Println("✅ Обновлено")
			} else {
				fmt.Println("❌ Ошибка")
			}
		case "6":
			fmt.Print("ID для удаления: ")
			scanner.Scan()
			idStr := scanner.Text()
			id, _ := strconv.Atoi(idStr)
			if deleteCard(f, id) {
				fmt.Println("✅ Удалено")
			} else {
				fmt.Println("❌ Не найдено")
			}
		case "7":
			fmt.Print("Имя файла (CSV): ")
			scanner.Scan()
			file := scanner.Text()
			if file == "" {
				file = "flashcards.csv"
			}
			exportCSV(f, file)
			fmt.Printf("Экспортировано в %s\n", file)
		case "8":
			fmt.Print("Имя файла (CSV): ")
			scanner.Scan()
			file := scanner.Text()
			if file == "" {
				fmt.Println("Укажите файл")
				continue
			}
			importCSV(f, file)
			fmt.Printf("Импортировано из %s\n", file)
		default:
			fmt.Println("Неверный выбор")
		}
	}
}
