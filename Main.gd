extends Control

@onready var launch = $MarginContainer/VBoxContainer/Button
@onready var game = $MarginContainer/VBoxContainer/OptionButton

var configs = [3,1,2,3]
func _ready():
	game.add_item("Assetto Corsa",0)
	game.add_item("Trackmania",1)
	game.add_item("SuperTuxKart",2)
	game.add_item("Automobilista",3)
	
	game.select(0)
	launch.connect("pressed",launch_server)

func launch_server():
	var op = OS.shell_open(OS.get_user_data_dir()+"/Serversconfig"+str(configs[game.selected])+".py")
	if op != 0:
		rtv.op = op
		get_tree().change_scene_to_file("res://error.tscn")
		print(op)
