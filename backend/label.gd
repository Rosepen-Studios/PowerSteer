
extends Label


# Called when the node enters the scene tree for the first time.
func _ready():
	text = text+OS.get_user_data_dir() + " (Exit code "+str(rtv.op)+")"
