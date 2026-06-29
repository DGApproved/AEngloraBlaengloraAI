from llama_cpp import Llama

print("--- INITIATING BARE-METAL LOAD ---")
try:
    llm = Llama(
        model_path="smollm2-360m.gguf", 
        n_ctx=512, 
        n_threads=1,
        n_gpu_layers=0,
        verbose=True
    )
    print("--- LOAD SUCCESSFUL ---")
    
    print("\nTesting inference...")
    # SmolLM2 uses standard ChatML formatting
    output = llm(
        "<|im_start|>user\nWhat is the capital of France?<|im_end|>\n<|im_start|>assistant\n", 
        max_tokens=20,
        stop=["<|im_end|>"]
    )
    print("RESULT:", output['choices'][0]['text'].strip())
    
except Exception as e:
    print(f"\n--- CRITICAL FAILURE ---")
    print(e)
