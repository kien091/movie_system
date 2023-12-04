package Source.Controllers;

import Source.Models.Movie;
import Source.Models.User;
import Source.Services.EmailService;
import Source.Services.MovieService;
import Source.Services.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Properties;

@SuppressWarnings("CallToPrintStackTrace")
@Controller
@RequestMapping("/")
public class HomeController {
    private final int PAGE_SIZE = 16;
    private final MovieService movieService;
    private final UserService userService;

    private final EmailService emailService;
    @Autowired
    public HomeController(MovieService movieService, UserService userService, EmailService emailService) {
        this.movieService = movieService;
        this.userService = userService;
        this.emailService = emailService;
    }

    @RequestMapping("")
    public String viewHome(Model model, HttpSession session) {
        List<Movie> movies = movieService.findAll()
                .stream()
                .filter(movie -> movie.getTotalEpisode() > 1)
                .toList();
        model.addAttribute("movies", movies);

        List<Movie> cartoon = movieService.findMoviesByGenre("Hoạt hình");
        model.addAttribute("cartoon", cartoon);

        List<Movie> favorite = movieService.findTop16ByOrderByTotalViewDesc();
        model.addAttribute("favorite", favorite);
        model.addAttribute("carousel", favorite);
        model.addAttribute("navigation", "Phim bộ");

        model.addAttribute("genres", movieService.getAllGenres());
        model.addAttribute("releaseDates", movieService.getAllReleaseDate());
        model.addAttribute("nations", movieService.getAllNation());
        model.addAttribute("top6MoviesNewest", movieService.top6NewestMovies());
        if(session.getAttribute("user") == null)
            model.addAttribute("showLogin", true);
        return "home";
    }

    @RequestMapping("/filter")
    public String filterBy(@RequestParam("category") String category,
                           @RequestParam(name = "page", defaultValue = "0") int page,
                           @RequestParam(name = "size", defaultValue = "16") int size,
                           Model model){
        Pageable pageable = PageRequest.of(page, size);
        Page<Movie> moviePage = movieService.filterMoviesByCategory(category, pageable);
        String navigation = switch (category) {
            case "series" -> "Phim bộ";
            case "feature-film" -> "Phim lẻ";
            case "complete" -> "Phim đã hoàn thành";
            case "english-language-films" -> "Phim chiếu rạp";
            default -> category;
        };
        model.addAttribute("navigation", navigation);

        model.addAttribute("movies", moviePage.getContent());
        model.addAttribute("moviesPage", moviePage);

        model.addAttribute("category", category);


        List<Movie> favorite = movieService.findTop16ByOrderByTotalViewDesc();
        model.addAttribute("carousel", favorite);


        model.addAttribute("genres", movieService.getAllGenres());
        model.addAttribute("releaseDates", movieService.getAllReleaseDate());
        model.addAttribute("nations", movieService.getAllNation());
        model.addAttribute("top6MoviesNewest", movieService.top6NewestMovies());
        return "home";
    }

    @RequestMapping(value = "/search", method = RequestMethod.GET)
    public String search(@RequestParam("search") String search, Model model) {
        List<Movie> movies = movieService.findAll()
                .stream()
                .filter(movie -> movie.getTitle().toLowerCase().contains(search.toLowerCase())
                                || movie.getGenre().toLowerCase().contains(search.toLowerCase())
                                || movie.getDirector().toLowerCase().contains(search.toLowerCase())
                                || movie.getNation().toLowerCase().contains(search.toLowerCase())
                                || movie.getReleaseDate().toLowerCase().contains(search.toLowerCase())
                                || movie.getActors().stream().anyMatch(actor ->
                                        actor.getName().toLowerCase().contains(search.toLowerCase())))
                .toList();
        model.addAttribute("movies", movies);
        model.addAttribute("navigation", "Kết quả tìm kiếm: \"" + search + "\"");
        return filterBy("search", 0, PAGE_SIZE, model);
    }

    @RequestMapping(value = "/filterBy", method = RequestMethod.GET)
    public String filterWithOption(@RequestParam("status") String status,
                                   @RequestParam("genre") String genre,
                                   @RequestParam("year") String year,
                                   @RequestParam("sort") String sort,
                                   Model model){
        List<Movie> movies = movieService.findAll();
        if(status.equals("Hoàn thành")){
            movies = movies.stream()
                    .filter(movie -> movie.getTotalEpisode() == movie.getEpisodes().size())
                    .toList();
        } else if (status.equals("Đang tiến hành")){
            movies = movies.stream()
                    .filter(movie -> movie.getTotalEpisode() > movie.getEpisodes().size())
                    .toList();
        }

        if(!genre.equals("Tất cả")){
            movies = movies.stream()
                    .filter(movie -> movie.getGenre().toLowerCase().contains(genre.toLowerCase()))
                    .toList();
        }

        if(!year.equals("Tất cả")){
            movies = movies.stream()
                    .filter(movie -> movie.getReleaseDate().toLowerCase().contains(year.toLowerCase()))
                    .toList();
        }

        if(sort.equals("Lượt xem")){
            movies = movies.stream()
                    .sorted((movie1, movie2) -> movie2.getTotalView() - movie1.getTotalView())
                    .toList();
        } else if (sort.equals("Đánh giá")){
            movies = movies.stream()
                    .sorted((movie1, movie2) -> (int) (movie2.getRating() - movie1.getRating()))
                    .toList();
        }

        model.addAttribute("movies", movies);
        model.addAttribute("navigation",
                "Kết quả tìm kiếm: \" Lọc \"");

        return filterBy("search", 0, PAGE_SIZE, model);
    }

    @PostMapping(value = "/login")
    public String login(@RequestParam("email") String email,
                        @RequestParam("password") String password,
                        Model model, HttpSession session){
        User user = new User(email, password);
        if(userService.authenticateUser(user)){
            session.setAttribute("user", user);
        } else {
            model.addAttribute("error", "Email hoặc mật khẩu không đúng");
        }
        return viewHome(model, session);
    }

    @PostMapping(value = "/reset")
    public String reset(@RequestParam("email") String email,
                        Model model, HttpSession session){
        model.addAttribute("showReset", true);
        List<User> users = userService.findAll();
        User user = users.stream()
                .filter(u -> u.getEmail().equals(email))
                .findFirst()
                .orElse(null);
        if(user == null){
            model.addAttribute("reset", "Tài khoản chưa được đăng kí");
        } else {
            StringBuilder passwordBuilder = new StringBuilder();
            for(int i = 0; i < 8; i++){
                passwordBuilder.append((char) (Math.random() * 26 + 'a'));
            }

            // use bcrypt to hash password and save to database
            String newPassword = passwordBuilder.toString();
            BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
            String bcryptPassword = bcrypt.encode(newPassword);
            user.setPassword(bcryptPassword);
            userService.update(user);

            sendEmail(email, newPassword);

            model.addAttribute("reset", "Mật khẩu đã được gửi về email của bạn");
        }
        return viewHome(model, session);
    }

    private void sendEmail(String email, String newPassword){
        String from = "nguyntrungkin091@gmail.com";

        String subject = "Bạn vừa yêu cầu đặt lại mật khẩu";
        String body = "Mật khẩu mới của bạn là: " + newPassword;
        emailService.sendEmail(from, email,subject, body);
    }
}
